package musinsa.pay.point_server.persistence.idempotency;

import jakarta.persistence.LockModeType;
import musinsa.pay.point_server.domain.idempotency.IdempotencyStatus;
import musinsa.pay.point_server.domain.idempotency.PointIdempotency;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PointIdempotencyRepository extends JpaRepository<PointIdempotency, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT i
        FROM PointIdempotency i
        WHERE i.accountId = :accountId
          AND i.requestType = :requestType
          AND i.idempotencyKey = :idempotencyKey
        """
    )
    Optional<PointIdempotency> findByKeyForUpdate(
        @Param("accountId") Long accountId,
        @Param("requestType") TransactionType requestType,
        @Param("idempotencyKey") String idempotencyKey
    );

    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE PointIdempotency i
        SET i.status = :successStatus,
            i.transactionId = :transactionId,
            i.errorCode = null,
            i.errorMessage = null
        WHERE i.id = :id
          AND i.status = :processingStatus
        """
    )
    int markSuccess(
        @Param("id") Long id,
        @Param("transactionId") Long transactionId,
        @Param("successStatus") IdempotencyStatus successStatus,
        @Param("processingStatus") IdempotencyStatus processingStatus
    );
}
