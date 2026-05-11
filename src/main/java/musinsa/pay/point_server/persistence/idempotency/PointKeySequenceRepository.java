package musinsa.pay.point_server.persistence.idempotency;

import jakarta.persistence.LockModeType;
import musinsa.pay.point_server.domain.idempotency.PointKeySequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PointKeySequenceRepository extends JpaRepository<PointKeySequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT s 
        FROM PointKeySequence s 
        WHERE s.dateKey = :dateKey
        """
    )
    Optional<PointKeySequence> findByIdForUpdate(
        @Param("dateKey") String dateKey
    );
}
