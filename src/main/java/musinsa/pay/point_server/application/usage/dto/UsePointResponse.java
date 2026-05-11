package musinsa.pay.point_server.application.usage.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 포인트 사용 API 응답 값
 */
public record UsePointResponse(
    String pointKey,
    Long transactionId,
    Long usageId,
    Long accountId,
    String orderNo,
    BigDecimal amount,
    BigDecimal balanceBefore,
    BigDecimal balanceAfter,
    List<UsePointAllocationResponse> allocations
) {
}
