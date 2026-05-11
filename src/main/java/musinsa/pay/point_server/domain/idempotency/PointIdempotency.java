package musinsa.pay.point_server.domain.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import musinsa.pay.point_server.domain.common.BaseEntity;
import musinsa.pay.point_server.domain.transaction.TransactionType;

@Entity
@Getter
@Table(
    name = "point_idempotency",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_point_idempotency_key",
            columnNames = {"account_id", "request_type", "idempotency_key"}
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointIdempotency extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 30)
    private TransactionType requestType;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyStatus status;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Builder
    private PointIdempotency(
        Long accountId,
        TransactionType requestType,
        String idempotencyKey,
        String requestHash,
        Long transactionId,
        String errorCode,
        String errorMessage,
        IdempotencyStatus status
    ) {
        this.accountId = accountId;
        this.requestType = requestType;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.transactionId = transactionId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.status = status;
    }
}
