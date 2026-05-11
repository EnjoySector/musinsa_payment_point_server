package musinsa.pay.point_server.application.point.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import musinsa.pay.point_server.domain.transaction.CreatedByType;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.transaction.TransactionType;

public record PointTransactionResponse(
    String pointKey,
    Long transactionId,
    TransactionType transactionType,
    BigDecimal amount,
    String orderNo,
    Long relatedTransactionId,
    CreatedByType createdByType,
    String createdById,
    String reason,
    LocalDateTime createdAt
) {

    public static PointTransactionResponse from(PointTransaction transaction) {
        return new PointTransactionResponse(
            transaction.getPointKey(),
            transaction.getId(),
            transaction.getTransactionType(),
            transaction.getAmount(),
            transaction.getOrderNo(),
            transaction.getRelatedTransactionId(),
            transaction.getCreatedByType(),
            transaction.getCreatedById(),
            transaction.getReason(),
            transaction.getCreatedAt()
        );
    }
}
