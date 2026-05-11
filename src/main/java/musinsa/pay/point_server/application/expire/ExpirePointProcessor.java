package musinsa.pay.point_server.application.expire;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.common.generator.PointKeyGenerator;
import musinsa.pay.point_server.domain.account.PointBalance;
import musinsa.pay.point_server.domain.earn.EarnStatus;
import musinsa.pay.point_server.domain.earn.PointEarn;
import musinsa.pay.point_server.domain.ledger.LedgerType;
import musinsa.pay.point_server.domain.ledger.PointLedger;
import musinsa.pay.point_server.domain.transaction.CreatedByType;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import musinsa.pay.point_server.persistence.account.PointBalanceRepository;
import musinsa.pay.point_server.persistence.earn.PointEarnRepository;
import musinsa.pay.point_server.persistence.ledger.PointLedgerRepository;
import musinsa.pay.point_server.persistence.transaction.PointTransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 만료 프로세서
 * - 만료 대상 적립 내역 조회
 * - 만료 트랜잭션 생성
 * - 적립 내역 상태 업데이트
 * - 계정 잔액 업데이트
 * - 원장 기록
 * - 만료 결과 조립
 */
@Component
@RequiredArgsConstructor
public class ExpirePointProcessor {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String SYSTEM_ACTOR = "EXPIRE";
    private static final String EXPIRE_REASON = "포인트 만료";

    private final PointKeyGenerator pointKeyGenerator;
    private final PointBalanceRepository balanceRepository;
    private final PointEarnRepository earnRepository;
    private final PointTransactionRepository transactionRepository;
    private final PointLedgerRepository ledgerRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public ExpirePointResult expire(PointBalance balance) {
        List<PointEarn> expiredEarns = findExpiredEarns(balance.getAccountId());
        if (expiredEarns.isEmpty()) {
            return ExpirePointResult.empty(balance.getAccountId(), balance.getBalanceAmount());
        }

        Map<Long, PointTransaction> originTransactions = findOriginTransactions(expiredEarns);
        return expireEarns(balance, expiredEarns, originTransactions);
    }

    private List<PointEarn> findExpiredEarns(Long accountId) {
        return earnRepository.findExpiredEarnsForUpdate(
            accountId,
            EarnStatus.AVAILABLE,
            ZERO,
            LocalDateTime.now()
        );
    }

    private Map<Long, PointTransaction> findOriginTransactions(List<PointEarn> expiredEarns) {
        List<Long> transactionIds = expiredEarns.stream()
            .map(PointEarn::getTransactionId)
            .toList();
        return transactionRepository.findAllById(transactionIds)
            .stream()
            .collect(Collectors.toMap(PointTransaction::getId, Function.identity()));
    }

    private ExpirePointResult expireEarns(
        PointBalance balance,
        List<PointEarn> expiredEarns,
        Map<Long, PointTransaction> originTransactions
    ) {
        BigDecimal balanceBefore = balance.getBalanceAmount();
        BigDecimal runningBalance = balanceBefore;
        List<ExpirePointResult.ExpiredEarnResult> results = new ArrayList<>();

        for (PointEarn earn : expiredEarns) {
            ExpirePointResult.ExpiredEarnResult result = expireEarn(
                earn,
                originTransactions,
                runningBalance
            );
            results.add(result);
            runningBalance = result.balanceAfter();
        }

        updateBalance(balance.getAccountId(), runningBalance);
        return new ExpirePointResult(
            balance.getAccountId(),
            balanceBefore,
            runningBalance,
            totalExpiredAmount(results),
            results
        );
    }

    private void updateBalance(
        Long accountId,
        BigDecimal balanceAfter
    ) {
        int updatedRows = balanceRepository.updateBalanceAmount(accountId, balanceAfter);
        if (updatedRows != 1) {
            throw new BaseException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    private ExpirePointResult.ExpiredEarnResult expireEarn(
        PointEarn earn,
        Map<Long, PointTransaction> originTransactions,
        BigDecimal balanceBefore
    ) {
        PointTransaction originTransaction = getOriginTransaction(earn, originTransactions);
        BigDecimal expiredAmount = earn.getAvailableAmount();
        BigDecimal balanceAfter = balanceBefore.subtract(expiredAmount);
        PointTransaction transaction = createTransaction(earn, originTransaction, expiredAmount);

        updateEarn(earn, expiredAmount);
        createLedger(transaction, earn, expiredAmount, balanceBefore, balanceAfter);

        return new ExpirePointResult.ExpiredEarnResult(
            earn.getId(),
            transaction.getId(),
            transaction.getPointKey(),
            expiredAmount,
            balanceBefore,
            balanceAfter
        );
    }

    private PointTransaction getOriginTransaction(
        PointEarn earn,
        Map<Long, PointTransaction> originTransactions
    ) {
        PointTransaction transaction = originTransactions.get(earn.getTransactionId());
        if (transaction == null) {
            throw new BaseException(ErrorCode.POINT_TRANSACTION_NOT_FOUND);
        }
        return transaction;
    }

    private PointTransaction createTransaction(
        PointEarn earn,
        PointTransaction originTransaction,
        BigDecimal expiredAmount
    ) {
        return transactionRepository.save(PointTransaction.builder()
            .pointKey(pointKeyGenerator.generate())
            .accountId(earn.getAccountId())
            .pointPolicyId(originTransaction.getPointPolicyId())
            .pointUserPolicyId(originTransaction.getPointUserPolicyId())
            .transactionType(TransactionType.EXPIRE)
            .amount(expiredAmount)
            .relatedTransactionId(originTransaction.getId())
            .createdByType(CreatedByType.SYSTEM)
            .createdById(SYSTEM_ACTOR)
            .reason(EXPIRE_REASON)
            .build());
    }

    private void updateEarn(
        PointEarn earn,
        BigDecimal expiredAmount
    ) {
        int updatedRows = earnRepository.expireEarn(
            earn.getId(),
            earn.getAccountId(),
            ZERO,
            earn.getExpiredAmount().add(expiredAmount),
            EarnStatus.EXPIRED,
            EarnStatus.AVAILABLE,
            expiredAmount
        );
        if (updatedRows != 1) {
            throw new BaseException(ErrorCode.POINT_EARN_NOT_FOUND);
        }
    }

    private void createLedger(
        PointTransaction transaction,
        PointEarn earn,
        BigDecimal expiredAmount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter
    ) {
        ledgerRepository.save(PointLedger.builder()
            .transactionId(transaction.getId())
            .accountId(earn.getAccountId())
            .earnId(earn.getId())
            .ledgerType(LedgerType.EXPIRE_DECREASE)
            .deltaAmount(expiredAmount.negate())
            .accountBalanceBefore(balanceBefore)
            .accountBalanceAfter(balanceAfter)
            .earnAvailableBefore(expiredAmount)
            .earnAvailableAfter(ZERO)
            .description(EXPIRE_REASON)
            .build());
    }

    private BigDecimal totalExpiredAmount(List<ExpirePointResult.ExpiredEarnResult> results) {
        return results.stream()
            .map(ExpirePointResult.ExpiredEarnResult::expiredAmount)
            .reduce(ZERO, BigDecimal::add);
    }
}
