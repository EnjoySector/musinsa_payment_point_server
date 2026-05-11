package musinsa.pay.point_server.persistence.account;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import musinsa.pay.point_server.domain.account.PointBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PointBalanceRepository extends JpaRepository<PointBalance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT b 
        FROM PointBalance b 
        WHERE b.accountId = :accountId
        """
    )
    Optional<PointBalance> findByAccountIdForUpdate(
        @Param("accountId") Long accountId
    );

    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE PointBalance b
        SET b.balanceAmount = :balanceAmount,
            b.updatedAt = CURRENT_TIMESTAMP
        WHERE b.accountId = :accountId
        """
    )
    int updateBalanceAmount(
        @Param("accountId") Long accountId,
        @Param("balanceAmount") BigDecimal balanceAmount
    );
}
