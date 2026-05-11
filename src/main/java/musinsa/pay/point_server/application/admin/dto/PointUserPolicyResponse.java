package musinsa.pay.point_server.application.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import musinsa.pay.point_server.domain.policy.PointUserPolicy;
import musinsa.pay.point_server.domain.policy.PolicyStatus;

public record PointUserPolicyResponse(
    Long id,
    String policyCode,
    String name,
    BigDecimal maxBalanceAmount,
    PolicyStatus status,
    String statusUpdatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static PointUserPolicyResponse from(PointUserPolicy policy) {
        return new PointUserPolicyResponse(
            policy.getId(),
            policy.getPolicyCode(),
            policy.getName(),
            policy.getMaxBalanceAmount(),
            policy.getStatus(),
            policy.getStatusUpdatedBy(),
            policy.getCreatedAt(),
            policy.getUpdatedAt()
        );
    }
}
