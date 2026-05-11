package musinsa.pay.point_server.persistence.earn;

import java.util.Optional;
import musinsa.pay.point_server.domain.earn.PointEarnCancel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointEarnCancelRepository extends JpaRepository<PointEarnCancel, Long> {

    Optional<PointEarnCancel> findByTransactionId(
        Long transactionId
    );
}
