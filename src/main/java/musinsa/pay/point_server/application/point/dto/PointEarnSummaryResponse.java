package musinsa.pay.point_server.application.point.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import musinsa.pay.point_server.domain.earn.EarnStatus;
import musinsa.pay.point_server.domain.earn.EarnType;
import musinsa.pay.point_server.domain.earn.PointEarn;
import musinsa.pay.point_server.domain.transaction.PointTransaction;

public record PointEarnSummaryResponse(
    String pointKey,
    Long transactionId,
    Long earnId,
    EarnType earnType,
    EarnStatus status,
    BigDecimal earnAmount,
    BigDecimal availableAmount,
    BigDecimal consumedAmount,
    BigDecimal cancelledAmount,
    BigDecimal expiredAmount,
    LocalDateTime expiresAt,
    String reason,
    LocalDateTime createdAt
) {

    public static PointEarnSummaryResponse of(
        PointTransaction transaction,
        PointEarn earn
    ) {
        return new PointEarnSummaryResponse(
            transaction.getPointKey(),
            transaction.getId(),
            earn.getId(),
            earn.getEarnType(),
            earn.getStatus(),
            earn.getEarnAmount(),
            earn.getAvailableAmount(),
            earn.getConsumedAmount(),
            earn.getCancelledAmount(),
            earn.getExpiredAmount(),
            earn.getExpiresAt(),
            transaction.getReason(),
            earn.getCreatedAt()
        );
    }
}
