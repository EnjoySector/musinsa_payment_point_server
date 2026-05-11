package musinsa.pay.point_server.application.earn;

import musinsa.pay.point_server.domain.earn.PointEarnCancel;
import musinsa.pay.point_server.domain.transaction.PointTransaction;

/**
 * 적립취소 처리 후 생성된 거래/취소 식별자
 */
public record EarnCancelCreateResult(
    Long transactionId,
    Long earnCancelId
) {

    public static EarnCancelCreateResult from(
        PointTransaction transaction,
        PointEarnCancel earnCancel
    ) {
        return new EarnCancelCreateResult(transaction.getId(), earnCancel.getId());
    }
}
