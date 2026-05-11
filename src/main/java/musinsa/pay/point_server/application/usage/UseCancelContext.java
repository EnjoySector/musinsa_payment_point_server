package musinsa.pay.point_server.application.usage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import musinsa.pay.point_server.application.usage.dto.UseCancelRequest;
import musinsa.pay.point_server.domain.account.PointAccount;
import musinsa.pay.point_server.domain.earn.EarnStatus;
import musinsa.pay.point_server.domain.earn.EarnType;
import musinsa.pay.point_server.domain.earn.PointEarn;
import musinsa.pay.point_server.domain.ledger.LedgerType;
import musinsa.pay.point_server.domain.ledger.PointLedger;
import musinsa.pay.point_server.domain.policy.PointPolicy;
import musinsa.pay.point_server.domain.transaction.CreatedByType;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import musinsa.pay.point_server.domain.usage.PointUsage;
import musinsa.pay.point_server.domain.usage.PointUsageCancel;
import musinsa.pay.point_server.domain.usage.PointUsageCancelAllocation;
import musinsa.pay.point_server.domain.usage.RestoreType;

/**
 * 포인트 사용취소 처리 중 필요한 사용, 정책, 금액 계산 결과 처리 컨텍스트
 */
public record UseCancelContext(
    UseCancelRequest request,
    PointAccount account,
    PointTransaction useTransaction,
    PointUsage usage,
    PointPolicy pointPolicy,
    BigDecimal cancelAmount,
    CreatedByType createdByType,
    String createdById,
    BigDecimal balanceBefore,
    BigDecimal balanceAfter
) {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public PointTransaction toTransaction(String pointKey) {
        return PointTransaction.builder()
            .pointKey(pointKey)
            .accountId(account.getId())
            .pointPolicyId(pointPolicy.getId())
            .pointUserPolicyId(account.getPointUserPolicyId())
            .transactionType(TransactionType.USE_CANCEL)
            .amount(cancelAmount)
            .orderNo(usage.getOrderNo())
            .relatedTransactionId(useTransaction.getId())
            .createdByType(createdByType)
            .createdById(createdById)
            .reason(request.reason())
            .build();
    }

    public PointUsageCancel toUsageCancel(Long transactionId) {
        return PointUsageCancel.builder()
            .transactionId(transactionId)
            .accountId(account.getId())
            .usageId(usage.getId())
            .cancelAmount(cancelAmount)
            .build();
    }

    public PointTransaction toReEarnTransaction(
        String pointKey,
        Long useCancelTransactionId,
        BigDecimal amount
    ) {
        return PointTransaction.builder()
            .pointKey(pointKey)
            .accountId(account.getId())
            .pointPolicyId(pointPolicy.getId())
            .pointUserPolicyId(account.getPointUserPolicyId())
            .transactionType(TransactionType.EARN)
            .amount(amount)
            .relatedTransactionId(useCancelTransactionId)
            .createdByType(CreatedByType.SYSTEM)
            .createdById("USE_CANCEL")
            .reason("사용취소 만료분 재적립")
            .build();
    }

    public PointEarn toReEarn(
        Long transactionId,
        Long usageCancelId,
        Long originalEarnId,
        BigDecimal amount
    ) {
        return PointEarn.builder()
            .transactionId(transactionId)
            .accountId(account.getId())
            .earnType(EarnType.USE_CANCEL_RESTORE)
            .earnAmount(amount)
            .availableAmount(amount)
            .consumedAmount(ZERO)
            .cancelledAmount(ZERO)
            .expiredAmount(ZERO)
            .expiresAt(LocalDateTime.now().plusDays(pointPolicy.getDefaultExpireDays()))
            .status(EarnStatus.AVAILABLE)
            .originalEarnId(originalEarnId)
            .sourceUsageCancelId(usageCancelId)
            .build();
    }

    public PointUsageCancelAllocation toCancelAllocation(
        Long usageCancelId,
        PointUsageCancelAllocationCandidate candidate,
        Long restoredEarnId
    ) {
        return PointUsageCancelAllocation.builder()
            .usageCancelId(usageCancelId)
            .usageAllocationId(candidate.usageAllocation().getId())
            .originalEarnId(candidate.originalEarn().getId())
            .restoredEarnId(restoredEarnId)
            .cancelAmount(candidate.cancelAmount())
            .restoreType(candidate.restoreType())
            .build();
    }

    public PointLedger toLedger(
        Long transactionId,
        Long usageCancelId,
        PointUsageCancelAllocationResult allocation,
        BigDecimal accountBalanceBefore,
        BigDecimal accountBalanceAfter
    ) {
        return PointLedger.builder()
            .transactionId(transactionId)
            .accountId(account.getId())
            .earnId(allocation.restoredEarnId())
            .usageId(usage.getId())
            .usageCancelId(usageCancelId)
            .usageAllocationId(allocation.usageAllocationId())
            .ledgerType(ledgerType(allocation))
            .deltaAmount(allocation.cancelAmount())
            .accountBalanceBefore(accountBalanceBefore)
            .accountBalanceAfter(accountBalanceAfter)
            .earnAvailableBefore(allocation.earnAvailableBefore())
            .earnAvailableAfter(allocation.earnAvailableAfter())
            .description("포인트 사용취소")
            .build();
    }

    private LedgerType ledgerType(PointUsageCancelAllocationResult allocation) {
        return allocation.restoreType() == RestoreType.ORIGINAL_EARN
            ? LedgerType.USE_CANCEL_ORIGINAL_INCREASE
            : LedgerType.USE_CANCEL_NEW_EARN_INCREASE;
    }
}
