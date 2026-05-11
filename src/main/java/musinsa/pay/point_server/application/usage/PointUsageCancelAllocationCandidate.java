package musinsa.pay.point_server.application.usage;

import java.math.BigDecimal;
import musinsa.pay.point_server.domain.earn.EarnStatus;
import musinsa.pay.point_server.domain.earn.PointEarn;
import musinsa.pay.point_server.domain.usage.PointUsageAllocation;
import musinsa.pay.point_server.domain.usage.RestoreType;
import musinsa.pay.point_server.domain.usage.UsageAllocationStatus;

/**
 * 포인트 사용취소 전 사용 배분 복원 후보
 */
public record PointUsageCancelAllocationCandidate(
    PointUsageAllocation usageAllocation,
    PointEarn originalEarn,
    BigDecimal cancelAmount,
    BigDecimal allocationCancelledAfter,
    UsageAllocationStatus allocationStatusAfter,
    RestoreType restoreType,
    BigDecimal originalEarnAvailableBefore,
    BigDecimal originalEarnAvailableAfter,
    BigDecimal originalEarnConsumedAfter,
    EarnStatus originalEarnStatusAfter
) {

    public boolean requiresNewEarn() {
        return restoreType == RestoreType.NEW_EARN;
    }
}
