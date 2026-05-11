package musinsa.pay.point_server.domain.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import musinsa.pay.point_server.domain.common.BaseCreatedEntity;

import java.math.BigDecimal;

@Entity
@Getter
@Table(name = "point_ledger")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointLedger extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private Long transactionId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "earn_id", updatable = false)
    private Long earnId;

    @Column(name = "usage_id", updatable = false)
    private Long usageId;

    @Column(name = "usage_cancel_id", updatable = false)
    private Long usageCancelId;

    @Column(name = "usage_allocation_id", updatable = false)
    private Long usageAllocationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ledger_type", nullable = false, length = 50, updatable = false)
    private LedgerType ledgerType;

    @Column(name = "delta_amount", nullable = false, updatable = false, precision = 15, scale = 0)
    private BigDecimal deltaAmount;

    @Column(name = "account_balance_before", nullable = false, updatable = false, precision = 15, scale = 0)
    private BigDecimal accountBalanceBefore;

    @Column(name = "account_balance_after", nullable = false, updatable = false, precision = 15, scale = 0)
    private BigDecimal accountBalanceAfter;

    @Column(name = "earn_available_before", updatable = false, precision = 15, scale = 0)
    private BigDecimal earnAvailableBefore;

    @Column(name = "earn_available_after", updatable = false, precision = 15, scale = 0)
    private BigDecimal earnAvailableAfter;

    @Column(length = 500, updatable = false)
    private String description;

    @Builder
    private PointLedger(
        Long transactionId,
        Long accountId,
        Long earnId,
        Long usageId,
        Long usageCancelId,
        Long usageAllocationId,
        LedgerType ledgerType,
        BigDecimal deltaAmount,
        BigDecimal accountBalanceBefore,
        BigDecimal accountBalanceAfter,
        BigDecimal earnAvailableBefore,
        BigDecimal earnAvailableAfter,
        String description
    ) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.earnId = earnId;
        this.usageId = usageId;
        this.usageCancelId = usageCancelId;
        this.usageAllocationId = usageAllocationId;
        this.ledgerType = ledgerType;
        this.deltaAmount = deltaAmount;
        this.accountBalanceBefore = accountBalanceBefore;
        this.accountBalanceAfter = accountBalanceAfter;
        this.earnAvailableBefore = earnAvailableBefore;
        this.earnAvailableAfter = earnAvailableAfter;
        this.description = description;
    }
}
