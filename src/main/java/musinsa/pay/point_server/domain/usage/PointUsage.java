package musinsa.pay.point_server.domain.usage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import musinsa.pay.point_server.domain.common.BaseEntity;

import java.math.BigDecimal;

@Entity
@Getter
@Table(
    name = "point_usage",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_point_usage_order",
            columnNames = {"account_id", "order_no"}
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUsage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private Long transactionId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "order_no", nullable = false, length = 100)
    private String orderNo;

    @Column(name = "usage_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal usageAmount;

    @Column(name = "cancelled_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal cancelledAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UsageStatus status;

    @Builder
    private PointUsage(
        Long transactionId,
        Long accountId,
        String orderNo,
        BigDecimal usageAmount,
        BigDecimal cancelledAmount,
        UsageStatus status
    ) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.orderNo = orderNo;
        this.usageAmount = usageAmount;
        this.cancelledAmount = cancelledAmount;
        this.status = status;
    }
}
