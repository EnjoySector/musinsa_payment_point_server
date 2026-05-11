package musinsa.pay.point_server.application.earn.dto;

import java.math.BigDecimal;

/**
 * 포인트 적립취소 API 응답 값
 */
public record EarnCancelResponse(
    String pointKey,
    Long transactionId,
    Long earnCancelId,
    Long accountId,
    String earnPointKey,
    Long earnId,
    BigDecimal cancelAmount,
    BigDecimal balanceBefore,
    BigDecimal balanceAfter
) {
}
