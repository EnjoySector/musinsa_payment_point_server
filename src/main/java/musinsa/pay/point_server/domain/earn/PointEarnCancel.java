package musinsa.pay.point_server.domain.earn;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import musinsa.pay.point_server.domain.common.BaseCreatedEntity;


@Entity
@Getter
@Table(name = "point_earn_cancel")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointEarnCancel extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private Long transactionId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "earn_id", nullable = false, unique = true)
    private Long earnId;

    @Column(name = "cancel_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal cancelAmount;

    @Builder
    private PointEarnCancel(
        Long transactionId,
        Long accountId,
        Long earnId,
        BigDecimal cancelAmount
    ) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.earnId = earnId;
        this.cancelAmount = cancelAmount;
    }
}
