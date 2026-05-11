package musinsa.pay.point_server.domain.usage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import musinsa.pay.point_server.domain.common.BaseEntity;

import java.math.BigDecimal;

@Getter
@Entity
@Table(
    name = "point_usage_allocation",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_usage_alloc_seq",
            columnNames = {"usage_id", "allocation_seq"}
        ),
        @UniqueConstraint(
            name = "uq_usage_alloc_earn",
            columnNames = {"usage_id", "earn_id"}
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUsageAllocation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usage_id", nullable = false)
    private Long usageId;

    @Column(name = "earn_id", nullable = false)
    private Long earnId;

    @Column(name = "allocation_seq", nullable = false)
    private Integer allocationSeq;

    @Column(name = "amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal amount;

    @Column(name = "cancelled_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal cancelledAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UsageAllocationStatus status;

    @Builder
    private PointUsageAllocation(
        Long usageId,
        Long earnId,
        Integer allocationSeq,
        BigDecimal amount,
        BigDecimal cancelledAmount,
        UsageAllocationStatus status
    ) {
        this.usageId = usageId;
        this.earnId = earnId;
        this.allocationSeq = allocationSeq;
        this.amount = amount;
        this.cancelledAmount = cancelledAmount;
        this.status = status;
    }
}

