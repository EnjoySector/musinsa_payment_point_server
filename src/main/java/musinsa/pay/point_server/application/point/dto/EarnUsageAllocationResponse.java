package musinsa.pay.point_server.application.point.dto;

import java.math.BigDecimal;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.usage.PointUsage;
import musinsa.pay.point_server.domain.usage.PointUsageAllocation;
import musinsa.pay.point_server.domain.usage.UsageAllocationStatus;
import musinsa.pay.point_server.domain.usage.UsageStatus;

public record EarnUsageAllocationResponse(
    String usePointKey,
    Long usageId,
    String orderNo,
    BigDecimal usedAmount,
    BigDecimal cancelledAmount,
    UsageAllocationStatus allocationStatus,
    UsageStatus usageStatus
) {

    public static EarnUsageAllocationResponse of(
        PointTransaction transaction,
        PointUsage usage,
        PointUsageAllocation allocation
    ) {
        return new EarnUsageAllocationResponse(
            transaction.getPointKey(),
            usage.getId(),
            usage.getOrderNo(),
            allocation.getAmount(),
            allocation.getCancelledAmount(),
            allocation.getStatus(),
            usage.getStatus()
        );
    }
}
