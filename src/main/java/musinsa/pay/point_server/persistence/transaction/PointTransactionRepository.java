package musinsa.pay.point_server.persistence.transaction;

import java.util.Optional;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    Optional<PointTransaction> findByPointKeyAndTransactionType(
        String pointKey,
        TransactionType transactionType
    );

    Page<PointTransaction> findByAccountIdOrderByIdDesc(
        Long accountId,
        Pageable pageable
    );
}
