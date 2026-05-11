package musinsa.pay.point_server.application.usage.dto;

import java.math.BigDecimal;

/**
 * 포인트 사용 적립별 배분 응답 값
 */
public record UsePointAllocationResponse(
    Long allocationId,
    Long earnId,
    Integer allocationSeq,
    BigDecimal amount
) {
}
