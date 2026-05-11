package musinsa.pay.point_server.persistence.earn;


import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import musinsa.pay.point_server.domain.earn.EarnStatus;
import musinsa.pay.point_server.domain.earn.EarnType;
import musinsa.pay.point_server.domain.earn.PointEarn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointEarnRepository extends JpaRepository<PointEarn, Long> {

    Optional<PointEarn> findByTransactionId(
        Long transactionId
    );

    List<PointEarn> findByAccountIdOrderByIdDesc(
        Long accountId
    );

    List<PointEarn> findByAccountIdAndEarnTypeOrderByIdDesc(
        Long accountId,
        EarnType earnType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT e
        FROM PointEarn e
        WHERE e.accountId = :accountId
          AND e.status = :status
          AND e.availableAmount > :zeroAmount
          AND e.expiresAt <= :now
        ORDER BY e.expiresAt ASC, e.id ASC
        """
    )
    List<PointEarn> findExpiredEarnsForUpdate(
        @Param("accountId") Long accountId,
        @Param("status") EarnStatus status,
        @Param("zeroAmount") BigDecimal zeroAmount,
        @Param("now") LocalDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT e
        FROM PointEarn e
        WHERE e.transactionId = :transactionId
        """
    )
    Optional<PointEarn> findByTransactionIdForUpdate(
        @Param("transactionId") Long transactionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT e
        FROM PointEarn e
        WHERE e.id = :earnId
        """
    )
    Optional<PointEarn> findByIdForUpdate(
        @Param("earnId") Long earnId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT e
        FROM PointEarn e
        WHERE e.accountId = :accountId
          AND e.status = :status
          AND e.availableAmount > :zeroAmount
          AND e.expiresAt > :now
        ORDER BY
          CASE WHEN e.earnType = :manualType THEN 0 ELSE 1 END,
          e.expiresAt ASC,
          e.id ASC
        """
    )
    List<PointEarn> findUsableEarnsForUpdate(
        @Param("accountId") Long accountId,
        @Param("status") EarnStatus status,
        @Param("zeroAmount") BigDecimal zeroAmount,
        @Param("now") LocalDateTime now,
        @Param("manualType") EarnType manualType
    );

    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE PointEarn e
        SET e.availableAmount = :zeroAmount,
            e.cancelledAmount = :cancelAmount,
            e.status = :status,
            e.updatedAt = CURRENT_TIMESTAMP
        WHERE e.id = :earnId
          AND e.accountId = :accountId
        """
    )
    int cancelEarn(
        @Param("earnId") Long earnId,
        @Param("accountId") Long accountId,
        @Param("zeroAmount") BigDecimal zeroAmount,
        @Param("cancelAmount") BigDecimal cancelAmount,
        @Param("status") EarnStatus status
    );

    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE PointEarn e
        SET e.availableAmount = :availableAmount,
            e.consumedAmount = :consumedAmount,
            e.status = :status,
            e.updatedAt = CURRENT_TIMESTAMP
        WHERE e.id = :earnId
          AND e.accountId = :accountId
        """
    )
    int useEarn(
        @Param("earnId") Long earnId,
        @Param("accountId") Long accountId,
        @Param("availableAmount") BigDecimal availableAmount,
        @Param("consumedAmount") BigDecimal consumedAmount,
        @Param("status") EarnStatus status
    );

    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE PointEarn e
        SET e.availableAmount = :availableAmount,
            e.consumedAmount = :consumedAmount,
            e.status = :status,
            e.updatedAt = CURRENT_TIMESTAMP
        WHERE e.id = :earnId
          AND e.accountId = :accountId
        """
    )
    int restoreEarn(
        @Param("earnId") Long earnId,
        @Param("accountId") Long accountId,
        @Param("availableAmount") BigDecimal availableAmount,
        @Param("consumedAmount") BigDecimal consumedAmount,
        @Param("status") EarnStatus status
    );

    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE PointEarn e
        SET e.availableAmount = :zeroAmount,
            e.expiredAmount = :expiredAmount,
            e.status = :expiredStatus,
            e.updatedAt = CURRENT_TIMESTAMP
        WHERE e.id = :earnId
          AND e.accountId = :accountId
          AND e.status = :availableStatus
          AND e.availableAmount = :expectedAvailableAmount
        """
    )
    int expireEarn(
        @Param("earnId") Long earnId,
        @Param("accountId") Long accountId,
        @Param("zeroAmount") BigDecimal zeroAmount,
        @Param("expiredAmount") BigDecimal expiredAmount,
        @Param("expiredStatus") EarnStatus expiredStatus,
        @Param("availableStatus") EarnStatus availableStatus,
        @Param("expectedAvailableAmount") BigDecimal expectedAvailableAmount
    );
}
