package musinsa.pay.point_server.application.usage;

import java.math.BigDecimal;
import musinsa.pay.point_server.application.usage.dto.UsePointRequest;
import musinsa.pay.point_server.domain.account.PointAccount;
import musinsa.pay.point_server.domain.ledger.LedgerType;
import musinsa.pay.point_server.domain.ledger.PointLedger;
import musinsa.pay.point_server.domain.transaction.CreatedByType;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import musinsa.pay.point_server.domain.usage.PointUsage;
import musinsa.pay.point_server.domain.usage.PointUsageAllocation;
import musinsa.pay.point_server.domain.usage.UsageAllocationStatus;
import musinsa.pay.point_server.domain.usage.UsageStatus;

/**
 * 포인트 사용 처리 중 필요한 계정, 주문, 금액 계산 결과 처리 컨텍스트
 */
public record UsePointContext(
    UsePointRequest request,
    PointAccount account,
    BigDecimal amount,
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
            .pointUserPolicyId(account.getPointUserPolicyId())
            .transactionType(TransactionType.USE)
            .amount(amount)
            .orderNo(request.orderNo())
            .createdByType(createdByType)
            .createdById(createdById)
            .reason(request.reason())
            .build();
    }

    public PointUsage toUsage(Long transactionId) {
        return PointUsage.builder()
            .transactionId(transactionId)
            .accountId(account.getId())
            .orderNo(request.orderNo())
            .usageAmount(amount)
            .cancelledAmount(ZERO)
            .status(UsageStatus.USED)
            .build();
    }

    public PointUsageAllocation toAllocation(
        Long usageId,
        PointUsageAllocationCandidate candidate
    ) {
        return PointUsageAllocation.builder()
            .usageId(usageId)
            .earnId(candidate.earn().getId())
            .allocationSeq(candidate.allocationSeq())
            .amount(candidate.amount())
            .cancelledAmount(ZERO)
            .status(UsageAllocationStatus.USED)
            .build();
    }

    public PointLedger toLedger(
        Long transactionId,
        Long usageId,
        PointUsageAllocationResult allocation,
        BigDecimal accountBalanceBefore,
        BigDecimal accountBalanceAfter
    ) {
        return PointLedger.builder()
            .transactionId(transactionId)
            .accountId(account.getId())
            .earnId(allocation.earnId())
            .usageId(usageId)
            .usageAllocationId(allocation.allocationId())
            .ledgerType(LedgerType.USE_DECREASE)
            .deltaAmount(allocation.amount().negate())
            .accountBalanceBefore(accountBalanceBefore)
            .accountBalanceAfter(accountBalanceAfter)
            .earnAvailableBefore(allocation.earnAvailableBefore())
            .earnAvailableAfter(allocation.earnAvailableAfter())
            .description("포인트 사용")
            .build();
    }
}
