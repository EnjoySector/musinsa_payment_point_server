package musinsa.pay.point_server.domain.policy;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import musinsa.pay.point_server.domain.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "point_user_policy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUserPolicy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_code", nullable = false, unique = true, length = 30)
    private String policyCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "max_balance_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal maxBalanceAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PolicyStatus status;

    @Column(name = "status_updated_at", nullable = false)
    private LocalDateTime statusUpdatedAt;

    @Column(name = "status_updated_by", nullable = false, length = 64)
    private String statusUpdatedBy;

    @Builder
    private PointUserPolicy(
        String policyCode,
        String name,
        BigDecimal maxBalanceAmount,
        PolicyStatus status,
        LocalDateTime statusUpdatedAt,
        String statusUpdatedBy
    ) {
        this.policyCode = policyCode;
        this.name = name;
        this.maxBalanceAmount = maxBalanceAmount;
        this.status = status;
        this.statusUpdatedAt = statusUpdatedAt;
        this.statusUpdatedBy = statusUpdatedBy;
    }

}

