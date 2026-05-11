package musinsa.pay.point_server.application.usage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.application.usage.dto.UseCancelRequest;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.account.AccountStatus;
import musinsa.pay.point_server.domain.account.PointAccount;
import musinsa.pay.point_server.domain.account.PointBalance;
import musinsa.pay.point_server.domain.policy.PointPolicy;
import musinsa.pay.point_server.domain.policy.PolicyStatus;
import musinsa.pay.point_server.domain.transaction.CreatedByType;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import musinsa.pay.point_server.domain.usage.PointUsage;
import musinsa.pay.point_server.persistence.account.PointAccountRepository;
import musinsa.pay.point_server.persistence.policy.PointPolicyRepository;
import musinsa.pay.point_server.persistence.transaction.PointTransactionRepository;
import musinsa.pay.point_server.persistence.usage.PointUsageRepository;
import org.springframework.stereotype.Component;

/**
 * 포인트 사용취소 처리 컨텍스트 생성 팩토리
 * 계정/사용 거래/정책 조회, 금액 계산
 */
@Component
@RequiredArgsConstructor
public class UseCancelContextFactory {

    private static final String DEFAULT_POINT_POLICY_CODE = "DEFAULT";

    private final PointAccountRepository accountRepository;
    private final PointTransactionRepository transactionRepository;
    private final PointUsageRepository usageRepository;
    private final PointPolicyRepository pointPolicyRepository;

    public UseCancelContext create(UseCancelRequest request, PointBalance balance) {
        PointAccount account = getActiveAccount(request.accountId());
        PointTransaction useTransaction = getUseTransaction(request.usePointKey());
        validateTransactionOwner(account.getId(), useTransaction);
        PointUsage usage = getUsage(useTransaction.getId());
        BigDecimal cancelAmount = normalizeAmount(request.cancelAmount());
        return createContext(request, balance, account, useTransaction, usage, cancelAmount);
    }

    private PointAccount getActiveAccount(Long accountId) {
        PointAccount account = accountRepository.findById(accountId)
            .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_FOUND));
        validateAccountStatus(account);
        return account;
    }

    private void validateAccountStatus(PointAccount account) {
        if (account.getStatus() == AccountStatus.BLOCKED) {
            throw new BaseException(ErrorCode.ACCOUNT_BLOCKED);
        }
        if (account.getStatus() == AccountStatus.DELETED) {
            throw new BaseException(ErrorCode.ACCOUNT_DELETED);
        }
    }

    private PointTransaction getUseTransaction(String usePointKey) {
        return transactionRepository
            .findByPointKeyAndTransactionType(usePointKey, TransactionType.USE)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_TRANSACTION_NOT_FOUND));
    }

    private void validateTransactionOwner(
        Long accountId,
        PointTransaction useTransaction
    ) {
        if (!Objects.equals(accountId, useTransaction.getAccountId())) {
            throw new BaseException(ErrorCode.POINT_USAGE_CANCEL_NOT_ALLOWED);
        }
    }

    private PointUsage getUsage(Long useTransactionId) {
        return usageRepository.findByTransactionIdForUpdate(useTransactionId)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_USAGE_NOT_FOUND));
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        try {
            return amount.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new BaseException(ErrorCode.POINT_AMOUNT_INVALID);
        }
    }

    private UseCancelContext createContext(
        UseCancelRequest request,
        PointBalance balance,
        PointAccount account,
        PointTransaction useTransaction,
        PointUsage usage,
        BigDecimal cancelAmount
    ) {
        return new UseCancelContext(
            request,
            account,
            useTransaction,
            usage,
            getActivePointPolicy(),
            cancelAmount,
            CreatedByType.USER,
            String.valueOf(account.getUserId()),
            balance.getBalanceAmount(),
            balance.getBalanceAmount().add(cancelAmount)
        );
    }

    private PointPolicy getActivePointPolicy() {
        return pointPolicyRepository.findByPolicyCodeAndStatus(DEFAULT_POINT_POLICY_CODE, PolicyStatus.ACTIVE)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_POLICY_NOT_FOUND));
    }

}
