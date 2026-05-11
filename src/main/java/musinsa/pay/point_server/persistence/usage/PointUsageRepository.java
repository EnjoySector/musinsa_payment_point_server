package musinsa.pay.point_server.persistence.usage;


import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import musinsa.pay.point_server.domain.usage.PointUsage;
import musinsa.pay.point_server.domain.usage.UsageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointUsageRepository extends JpaRepository<PointUsage, Long> {

    boolean existsByAccountIdAndOrderNo(
        Long accountId,
        String orderNo
    );

    Optional<PointUsage> findByTransactionId(
        Long transactionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT u
        FROM PointUsage u
        WHERE u.transactionId = :transactionId
        """
    )
    Optional<PointUsage> findByTransactionIdForUpdate(
        @Param("transactionId") Long transactionId
    );

    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE PointUsage u
        SET u.cancelledAmount = :cancelledAmount,
            u.status = :status,
            u.updatedAt = CURRENT_TIMESTAMP
        WHERE u.id = :usageId
          AND u.accountId = :accountId
        """
    )
    int updateCancelState(
        @Param("usageId") Long usageId,
        @Param("accountId") Long accountId,
        @Param("cancelledAmount") BigDecimal cancelledAmount,
        @Param("status") UsageStatus status
    );
}
