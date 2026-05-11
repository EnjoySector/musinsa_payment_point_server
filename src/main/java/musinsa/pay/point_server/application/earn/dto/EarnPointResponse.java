package musinsa.pay.point_server.application.earn.dto;

import musinsa.pay.point_server.domain.earn.EarnType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 포인트 적립 API 응답 값
 */
public record EarnPointResponse(
    String pointKey,
    Long transactionId,
    Long earnId,
    Long accountId,
    EarnType earnType,
    BigDecimal amount,
    BigDecimal balanceBefore,
    BigDecimal balanceAfter,
    LocalDateTime expiresAt
) {
}
