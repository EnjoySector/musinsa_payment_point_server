package musinsa.pay.point_server.persistence.ledger;

import musinsa.pay.point_server.domain.ledger.LedgerType;
import musinsa.pay.point_server.domain.ledger.PointLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PointLedgerRepository extends JpaRepository<PointLedger, Long> {

    Optional<PointLedger> findFirstByTransactionIdAndLedgerType(
        Long transactionId,
        LedgerType ledgerType
    );

    List<PointLedger> findByTransactionIdAndLedgerTypeOrderByIdAsc(
        Long transactionId,
        LedgerType ledgerType
    );

    List<PointLedger> findByTransactionIdAndLedgerTypeInOrderByIdAsc(
        Long transactionId,
        List<LedgerType> ledgerTypes
    );

    List<PointLedger> findByUsageCancelIdAndLedgerTypeInOrderByIdAsc(
        Long usageCancelId,
        List<LedgerType> ledgerTypes
    );
}
