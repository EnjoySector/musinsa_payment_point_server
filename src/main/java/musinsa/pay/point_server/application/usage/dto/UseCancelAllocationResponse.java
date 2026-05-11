package musinsa.pay.point_server.application.usage.dto;

import java.math.BigDecimal;
import musinsa.pay.point_server.domain.usage.RestoreType;

/**
 * 포인트 사용취소 배분 응답 값
 */
public record UseCancelAllocationResponse(
    Long cancelAllocationId,
    Long usageAllocationId,
    Long originalEarnId,
    Long restoredEarnId,
    BigDecimal cancelAmount,
    RestoreType restoreType
) {
}
