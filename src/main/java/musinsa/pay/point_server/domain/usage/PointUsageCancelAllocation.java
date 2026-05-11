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
@Table(
    name = "point_usage_cancel_allocation",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_cancel_alloc_usage_alloc",
            columnNames = {"usage_cancel_id", "usage_allocation_id"}
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUsageCancelAllocation extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usage_cancel_id", nullable = false)
    private Long usageCancelId;

    @Column(name = "usage_allocation_id", nullable = false)
    private Long usageAllocationId;

    @Column(name = "original_earn_id", nullable = false)
    private Long originalEarnId;

    @Column(name = "restored_earn_id", nullable = false)
    private Long restoredEarnId;

    @Column(name = "cancel_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal cancelAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "restore_type", nullable = false, length = 30)
    private RestoreType restoreType;

    @Builder
    private PointUsageCancelAllocation(
        Long usageCancelId,
        Long usageAllocationId,
        Long originalEarnId,
        Long restoredEarnId,
        BigDecimal cancelAmount,
        RestoreType restoreType
    ) {
        this.usageCancelId = usageCancelId;
        this.usageAllocationId = usageAllocationId;
        this.originalEarnId = originalEarnId;
        this.restoredEarnId = restoredEarnId;
        this.cancelAmount = cancelAmount;
        this.restoreType = restoreType;
    }
}
