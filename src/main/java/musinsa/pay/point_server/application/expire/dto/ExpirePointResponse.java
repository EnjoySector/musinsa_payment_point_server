package musinsa.pay.point_server.application.expire.dto;

import java.math.BigDecimal;
import java.util.List;
import musinsa.pay.point_server.application.expire.ExpirePointResult;

public record ExpirePointResponse(
    Long accountId,
    int expiredCount,
    BigDecimal expiredAmount,
    BigDecimal balanceBefore,
    BigDecimal balanceAfter,
    List<ExpiredEarnResponse> expiredEarns
) {

    public static ExpirePointResponse from(ExpirePointResult result) {
        return new ExpirePointResponse(
            result.accountId(),
            result.expiredCount(),
            result.expiredAmount(),
            result.balanceBefore(),
            result.balanceAfter(),
            result.expiredEarns()
                .stream()
                .map(ExpiredEarnResponse::from)
                .toList()
        );
    }
}
