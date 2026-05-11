package musinsa.pay.point_server.application.usage;

import java.util.List;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.usage.PointUsageCancel;

/**
 * 포인트 사용취소 처리 후 생성된 거래/취소/배분 식별자
 */
public record PointUsageCancelCreateResult(
    Long transactionId,
    Long usageCancelId,
    List<PointUsageCancelAllocationResult> allocations
) {

    public static PointUsageCancelCreateResult from(
        PointTransaction transaction,
        PointUsageCancel usageCancel,
        List<PointUsageCancelAllocationResult> allocations
    ) {
        return new PointUsageCancelCreateResult(transaction.getId(), usageCancel.getId(), allocations);
    }
}
