package musinsa.pay.point_server.application.usage;

import java.math.BigDecimal;
import musinsa.pay.point_server.domain.earn.EarnStatus;
import musinsa.pay.point_server.domain.earn.PointEarn;

/**
 * 포인트 사용 전 적립 차감 후보
 */
public record PointUsageAllocationCandidate(
    PointEarn earn,
    Integer allocationSeq,
    BigDecimal amount,
    BigDecimal earnAvailableBefore,
    BigDecimal earnAvailableAfter,
    BigDecimal earnConsumedAfter,
    EarnStatus earnStatusAfter
) {
}
