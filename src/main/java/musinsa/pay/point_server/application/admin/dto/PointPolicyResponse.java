package musinsa.pay.point_server.application.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import musinsa.pay.point_server.domain.policy.PointPolicy;
import musinsa.pay.point_server.domain.policy.PolicyStatus;

public record PointPolicyResponse(
    Long id,
    String policyCode,
    String name,
    BigDecimal maxEarnAmount,
    Integer defaultExpireDays,
    Integer minExpireDays,
    Integer maxExpireDays,
    PolicyStatus status,
    String statusUpdatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static PointPolicyResponse from(PointPolicy policy) {
        return new PointPolicyResponse(
            policy.getId(),
            policy.getPolicyCode(),
            policy.getName(),
            policy.getMaxEarnAmount(),
            policy.getDefaultExpireDays(),
            policy.getMinExpireDays(),
            policy.getMaxExpireDays(),
            policy.getStatus(),
            policy.getStatusUpdatedBy(),
            policy.getCreatedAt(),
            policy.getUpdatedAt()
        );
    }
}
