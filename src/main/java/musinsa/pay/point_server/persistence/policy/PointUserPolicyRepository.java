package musinsa.pay.point_server.persistence.policy;

import java.math.BigDecimal;
import java.util.List;
import musinsa.pay.point_server.domain.policy.PointUserPolicy;
import musinsa.pay.point_server.domain.policy.PolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointUserPolicyRepository extends JpaRepository<PointUserPolicy, Long> {

    List<PointUserPolicy> findAllByOrderByIdAsc();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE PointUserPolicy p
        SET p.name = :name,
            p.maxBalanceAmount = :maxBalanceAmount,
            p.status = :status,
            p.statusUpdatedAt = CURRENT_TIMESTAMP,
            p.statusUpdatedBy = :adminId,
            p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.id = :policyId
        """
    )
    int updatePolicy(
        @Param("policyId") Long policyId,
        @Param("name") String name,
        @Param("maxBalanceAmount") BigDecimal maxBalanceAmount,
        @Param("status") PolicyStatus status,
        @Param("adminId") String adminId
    );
}
