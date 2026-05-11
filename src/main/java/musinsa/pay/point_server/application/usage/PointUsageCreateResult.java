package musinsa.pay.point_server.application.usage;

import java.util.List;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.usage.PointUsage;

/**
 * 포인트 사용 처리 후 생성된 거래/사용/배분 식별자
 */
public record PointUsageCreateResult(
    Long transactionId,
    Long usageId,
    List<PointUsageAllocationResult> allocations
) {

    public static PointUsageCreateResult from(
        PointTransaction transaction,
        PointUsage usage,
        List<PointUsageAllocationResult> allocations
    ) {
        return new PointUsageCreateResult(transaction.getId(), usage.getId(), allocations);
    }
}
