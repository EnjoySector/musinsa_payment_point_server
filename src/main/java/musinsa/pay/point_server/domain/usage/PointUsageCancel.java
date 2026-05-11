package musinsa.pay.point_server.domain.usage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import musinsa.pay.point_server.domain.common.BaseCreatedEntity;

import java.math.BigDecimal;

@Entity
@Getter
@Table(name = "point_usage_cancel")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUsageCancel extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private Long transactionId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "usage_id", nullable = false)
    private Long usageId;

    @Column(name = "cancel_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal cancelAmount;

    @Builder
    private PointUsageCancel(
        Long transactionId,
        Long accountId,
        Long usageId,
        BigDecimal cancelAmount
    ) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.usageId = usageId;
        this.cancelAmount = cancelAmount;
    }
}
