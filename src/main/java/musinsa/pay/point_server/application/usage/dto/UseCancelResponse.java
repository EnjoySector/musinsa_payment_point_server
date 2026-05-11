package musinsa.pay.point_server.application.usage.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 포인트 사용취소 API 응답 값
 */
public record UseCancelResponse(
    String pointKey,
    Long transactionId,
    Long usageCancelId,
    Long accountId,
    String usePointKey,
    Long usageId,
    BigDecimal cancelAmount,
    BigDecimal balanceBefore,
    BigDecimal balanceAfter,
    List<UseCancelAllocationResponse> allocations
) {
}
