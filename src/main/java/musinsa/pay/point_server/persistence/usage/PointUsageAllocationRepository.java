package musinsa.pay.point_server.persistence.usage;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import musinsa.pay.point_server.domain.usage.PointUsageAllocation;
import musinsa.pay.point_server.domain.usage.UsageAllocationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointUsageAllocationRepository extends JpaRepository<PointUsageAllocation, Long> {

    List<PointUsageAllocation> findByUsageIdOrderByAllocationSeqAsc(
        Long usageId
    );

    List<PointUsageAllocation> findByEarnIdOrderByIdAsc(
        Long earnId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT a
        FROM PointUsageAllocation a
        WHERE a.usageId = :usageId
          AND a.cancelledAmount < a.amount
        ORDER BY a.allocationSeq ASC
        """
    )
    List<PointUsageAllocation> findCancelableAllocationsForUpdate(
        @Param("usageId") Long usageId
    );

    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE PointUsageAllocation a
        SET a.cancelledAmount = :cancelledAmount,
            a.status = :status,
            a.updatedAt = CURRENT_TIMESTAMP
        WHERE a.id = :allocationId
        """
    )
    int updateCancelState(
        @Param("allocationId") Long allocationId,
        @Param("cancelledAmount") BigDecimal cancelledAmount,
        @Param("status") UsageAllocationStatus status
    );
}
