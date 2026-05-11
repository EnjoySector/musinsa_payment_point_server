package musinsa.pay.point_server.application.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import musinsa.pay.point_server.domain.policy.PolicyStatus;

public record UpdatePointUserPolicyRequest(
    @Size(max = 100, message = "name은 100자 이하여야 합니다.")
    String name,

    @DecimalMin(value = "1", message = "maxBalanceAmount는 1 이상이어야 합니다.")
    @Digits(integer = 15, fraction = 0, message = "maxBalanceAmount는 소수 없이 최대 15자리여야 합니다.")
    BigDecimal maxBalanceAmount,

    PolicyStatus status,

    @NotBlank(message = "adminId는 필수입니다.")
    @Size(max = 64, message = "adminId는 64자 이하여야 합니다.")
    String adminId
) {
}
