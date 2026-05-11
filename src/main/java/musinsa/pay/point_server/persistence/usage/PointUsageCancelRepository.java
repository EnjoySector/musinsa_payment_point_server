package musinsa.pay.point_server.persistence.usage;

import java.util.Optional;
import musinsa.pay.point_server.domain.usage.PointUsageCancel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointUsageCancelRepository extends JpaRepository<PointUsageCancel, Long> {

    Optional<PointUsageCancel> findByTransactionId(
        Long transactionId
    );
}
