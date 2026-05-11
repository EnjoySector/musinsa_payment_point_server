package musinsa.pay.point_server.persistence.policy;

import java.math.BigDecimal;
import java.util.List;
import musinsa.pay.point_server.domain.policy.PointPolicy;
import musinsa.pay.point_server.domain.policy.PolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PointPolicyRepository extends JpaRepository<PointPolicy, Long> {

    Optional<PointPolicy> findByPolicyCodeAndStatus(
        String policyCode,
        PolicyStatus status
    );

    List<PointPolicy> findAllByOrderByIdAsc();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE PointPolicy p
        SET p.name = :name,
            p.maxEarnAmount = :maxEarnAmount,
            p.defaultExpireDays = :defaultExpireDays,
            p.minExpireDays = :minExpireDays,
            p.maxExpireDays = :maxExpireDays,
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
        @Param("maxEarnAmount") BigDecimal maxEarnAmount,
        @Param("defaultExpireDays") Integer defaultExpireDays,
        @Param("minExpireDays") Integer minExpireDays,
        @Param("maxExpireDays") Integer maxExpireDays,
        @Param("status") PolicyStatus status,
        @Param("adminId") String adminId
    );
}
