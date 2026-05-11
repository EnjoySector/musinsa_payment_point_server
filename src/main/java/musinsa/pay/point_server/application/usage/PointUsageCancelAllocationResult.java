package musinsa.pay.point_server.application.usage;

import java.math.BigDecimal;
import musinsa.pay.point_server.domain.usage.RestoreType;

/**
 * 포인트 사용취소 후 저장된 복원 배분 결과
 */
public record PointUsageCancelAllocationResult(
    Long cancelAllocationId,
    Long ledgerTransactionId,
    Long usageAllocationId,
    Long originalEarnId,
    Long restoredEarnId,
    BigDecimal cancelAmount,
    RestoreType restoreType,
    BigDecimal earnAvailableBefore,
    BigDecimal earnAvailableAfter
) {
}
