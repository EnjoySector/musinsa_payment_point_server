package musinsa.pay.point_server.domain.earn;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import musinsa.pay.point_server.domain.common.BaseEntity;

@Entity
@Getter
@Table(name = "point_earn")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointEarn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private Long transactionId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "earn_type", nullable = false, length = 40)
    private EarnType earnType;

    @Column(name = "earn_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal earnAmount;

    @Column(name = "available_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal availableAmount;

    @Column(name = "consumed_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal consumedAmount;

    @Column(name = "cancelled_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal cancelledAmount;

    @Column(name = "expired_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal expiredAmount;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EarnStatus status;

    @Column(name = "original_earn_id")
    private Long originalEarnId;

    @Column(name = "source_usage_cancel_id")
    private Long sourceUsageCancelId;

    @Builder
    private PointEarn(
        Long transactionId,
        Long accountId,
        EarnType earnType,
        BigDecimal earnAmount,
        BigDecimal availableAmount,
        BigDecimal consumedAmount,
        BigDecimal cancelledAmount,
        BigDecimal expiredAmount,
        LocalDateTime expiresAt,
        EarnStatus status,
        Long originalEarnId,
        Long sourceUsageCancelId
    ) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.earnType = earnType;
        this.earnAmount = earnAmount;
        this.availableAmount = availableAmount;
        this.consumedAmount = consumedAmount;
        this.cancelledAmount = cancelledAmount;
        this.expiredAmount = expiredAmount;
        this.expiresAt = expiresAt;
        this.status = status;
        this.originalEarnId = originalEarnId;
        this.sourceUsageCancelId = sourceUsageCancelId;
    }
}
