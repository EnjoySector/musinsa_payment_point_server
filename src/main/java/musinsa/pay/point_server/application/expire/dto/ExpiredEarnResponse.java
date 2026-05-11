package musinsa.pay.point_server.application.expire.dto;

import java.math.BigDecimal;
import musinsa.pay.point_server.application.expire.ExpirePointResult;

public record ExpiredEarnResponse(
    Long earnId,
    Long transactionId,
    String pointKey,
    BigDecimal expiredAmount,
    BigDecimal balanceBefore,
    BigDecimal balanceAfter
) {

    public static ExpiredEarnResponse from(ExpirePointResult.ExpiredEarnResult result) {
        return new ExpiredEarnResponse(
            result.earnId(),
            result.transactionId(),
            result.pointKey(),
            result.expiredAmount(),
            result.balanceBefore(),
            result.balanceAfter()
        );
    }
}
