package musinsa.pay.point_server.application.point;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.application.point.dto.EarnUsageAllocationResponse;
import musinsa.pay.point_server.application.point.dto.EarnUsageTraceResponse;
import musinsa.pay.point_server.application.point.dto.PointEarnSummaryResponse;
import musinsa.pay.point_server.application.point.dto.PointSummaryResponse;
import musinsa.pay.point_server.application.point.dto.PointTransactionResponse;
import musinsa.pay.point_server.application.point.dto.UserPolicySummary;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.account.PointAccount;
import musinsa.pay.point_server.domain.account.PointBalance;
import musinsa.pay.point_server.domain.earn.EarnType;
import musinsa.pay.point_server.domain.earn.PointEarn;
import musinsa.pay.point_server.domain.policy.PointUserPolicy;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import musinsa.pay.point_server.domain.usage.PointUsage;
import musinsa.pay.point_server.domain.usage.PointUsageAllocation;
import musinsa.pay.point_server.persistence.account.PointAccountRepository;
import musinsa.pay.point_server.persistence.account.PointBalanceRepository;
import musinsa.pay.point_server.persistence.earn.PointEarnRepository;
import musinsa.pay.point_server.persistence.policy.PointUserPolicyRepository;
import musinsa.pay.point_server.persistence.transaction.PointTransactionRepository;
import musinsa.pay.point_server.persistence.usage.PointUsageAllocationRepository;
import musinsa.pay.point_server.persistence.usage.PointUsageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 정보 조회 서비스
 * - 포인트 잔액, 적립/사용 내역, 적립 사용 추적 등 다양한 포인트 관련 정보를 조회하는 기능 제공
 */
@Service
@RequiredArgsConstructor
public class PointInfoService {

    private static final int MAX_TRANSACTION_LIMIT = 100;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PointAccountRepository accountRepository;
    private final PointBalanceRepository balanceRepository;
    private final PointUserPolicyRepository userPolicyRepository;
    private final PointEarnRepository earnRepository;
    private final PointTransactionRepository transactionRepository;
    private final PointUsageRepository usageRepository;
    private final PointUsageAllocationRepository allocationRepository;

    @Transactional(readOnly = true)
    public PointSummaryResponse getSummary(Long accountId) {
        PointAccount account = getAccount(accountId);
        PointBalance balance = getBalance(accountId);
        PointUserPolicy userPolicy = getUserPolicy(account.getPointUserPolicyId());
        List<PointEarn> earns = earnRepository.findByAccountIdOrderByIdDesc(accountId);

        return PointSummaryResponse.of(
            account,
            balance,
            availableAmount(earns, EarnType.NORMAL),
            availableAmount(earns, EarnType.MANUAL),
            availableAmount(earns, EarnType.USE_CANCEL_RESTORE),
            UserPolicySummary.from(userPolicy)
        );
    }

    @Transactional(readOnly = true)
    public List<PointTransactionResponse> getTransactions(
        Long accountId,
        int limit
    ) {
        getAccount(accountId);
        PageRequest pageRequest = PageRequest.of(0, normalizedLimit(limit));
        return transactionRepository.findByAccountIdOrderByIdDesc(accountId, pageRequest)
            .stream()
            .map(PointTransactionResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PointEarnSummaryResponse> getManualEarns(Long accountId) {
        getAccount(accountId);
        List<PointEarn> earns = earnRepository.findByAccountIdAndEarnTypeOrderByIdDesc(accountId, EarnType.MANUAL);
        return toEarnResponses(earns);
    }

    @Transactional(readOnly = true)
    public EarnUsageTraceResponse traceEarnUsages(
        Long accountId,
        String earnPointKey
    ) {
        PointTransaction transaction = getEarnTransaction(earnPointKey);
        if (!transaction.getAccountId().equals(accountId)) {
            throw new BaseException(ErrorCode.POINT_EARN_NOT_FOUND);
        }
        PointEarn earn = earnRepository.findByTransactionId(transaction.getId())
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_EARN_NOT_FOUND));
        List<PointUsageAllocation> allocations = allocationRepository.findByEarnIdOrderByIdAsc(earn.getId());

        return EarnUsageTraceResponse.of(transaction, earn, toUsageAllocationResponses(allocations));
    }

    private List<PointEarnSummaryResponse> toEarnResponses(List<PointEarn> earns) {
        if (earns.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, PointTransaction> transactions = findTransactionMap(
            earns.stream()
                .map(PointEarn::getTransactionId)
                .toList()
        );
        return earns.stream()
            .map(earn -> PointEarnSummaryResponse.of(earnTransaction(earn, transactions), earn))
            .toList();
    }

    private List<EarnUsageAllocationResponse> toUsageAllocationResponses(List<PointUsageAllocation> allocations) {
        if (allocations.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, PointUsage> usages = findUsageMap(allocations);
        Map<Long, PointTransaction> transactions = findTransactionMap(
            usages.values()
                .stream()
                .map(PointUsage::getTransactionId)
                .toList()
        );

        return allocations.stream()
            .map(allocation -> toUsageAllocationResponse(allocation, usages, transactions))
            .toList();
    }

    private EarnUsageAllocationResponse toUsageAllocationResponse(
        PointUsageAllocation allocation,
        Map<Long, PointUsage> usages,
        Map<Long, PointTransaction> transactions
    ) {
        PointUsage usage = usages.get(allocation.getUsageId());
        PointTransaction transaction = transactions.get(usage.getTransactionId());
        return EarnUsageAllocationResponse.of(transaction, usage, allocation);
    }

    private PointTransaction earnTransaction(
        PointEarn earn,
        Map<Long, PointTransaction> transactions
    ) {
        return transactions.get(earn.getTransactionId());
    }

    private Map<Long, PointUsage> findUsageMap(List<PointUsageAllocation> allocations) {
        return usageRepository.findAllById(
                allocations.stream()
                    .map(PointUsageAllocation::getUsageId)
                    .toList()
            )
            .stream()
            .collect(Collectors.toMap(PointUsage::getId, Function.identity()));
    }

    private Map<Long, PointTransaction> findTransactionMap(List<Long> transactionIds) {
        return transactionRepository.findAllById(transactionIds)
            .stream()
            .collect(Collectors.toMap(PointTransaction::getId, Function.identity()));
    }

    private BigDecimal availableAmount(
        List<PointEarn> earns,
        EarnType earnType
    ) {
        return earns.stream()
            .filter(earn -> earn.getEarnType() == earnType)
            .map(PointEarn::getAvailableAmount)
            .reduce(ZERO, BigDecimal::add);
    }

    private PointAccount getAccount(Long accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private PointBalance getBalance(Long accountId) {
        return balanceRepository.findById(accountId)
            .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private PointUserPolicy getUserPolicy(Long policyId) {
        return userPolicyRepository.findById(policyId)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_USER_POLICY_NOT_FOUND));
    }

    private PointTransaction getEarnTransaction(String earnPointKey) {
        return transactionRepository.findByPointKeyAndTransactionType(earnPointKey, TransactionType.EARN)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_EARN_NOT_FOUND));
    }

    private int normalizedLimit(int limit) {
        if (limit < 1) {
            return 20;
        }
        return Math.min(limit, MAX_TRANSACTION_LIMIT);
    }
}
