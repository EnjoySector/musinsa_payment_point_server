package musinsa.pay.point_server.application.point.dto;

import java.math.BigDecimal;
import musinsa.pay.point_server.domain.policy.PointUserPolicy;
import musinsa.pay.point_server.domain.policy.PolicyStatus;

public record UserPolicySummary(
    Long policyId,
    String policyCode,
    String name,
    BigDecimal maxBalanceAmount,
    PolicyStatus status
) {

    public static UserPolicySummary from(PointUserPolicy policy) {
        return new UserPolicySummary(
            policy.getId(),
            policy.getPolicyCode(),
            policy.getName(),
            policy.getMaxBalanceAmount(),
            policy.getStatus()
        );
    }
}
