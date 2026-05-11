package musinsa.pay.point_server.application.usage;

import java.math.BigDecimal;

/**
 * 포인트 사용 후 저장된 배분 결과
 */
public record PointUsageAllocationResult(
    Long allocationId,
    Long earnId,
    Integer allocationSeq,
    BigDecimal amount,
    BigDecimal earnAvailableBefore,
    BigDecimal earnAvailableAfter
) {

    public static PointUsageAllocationResult from(
        Long allocationId,
        PointUsageAllocationCandidate candidate
    ) {
        return new PointUsageAllocationResult(
            allocationId,
            candidate.earn().getId(),
            candidate.allocationSeq(),
            candidate.amount(),
            candidate.earnAvailableBefore(),
            candidate.earnAvailableAfter()
        );
    }
}
