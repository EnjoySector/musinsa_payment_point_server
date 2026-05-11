package musinsa.pay.point_server.application.earn;

import musinsa.pay.point_server.domain.earn.PointEarn;
import musinsa.pay.point_server.domain.transaction.PointTransaction;

/**
 * 적립 처리 후 생성된 거래/적립 식별자
 */
public record EarnPointCreateResult(
    Long transactionId,
    Long earnId
) {

    public static EarnPointCreateResult from(
        PointTransaction transaction,
        PointEarn earn
    ) {
        return new EarnPointCreateResult(transaction.getId(), earn.getId());
    }
}
