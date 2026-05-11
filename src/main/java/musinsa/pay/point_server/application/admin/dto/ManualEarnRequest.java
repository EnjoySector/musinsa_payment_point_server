package musinsa.pay.point_server.application.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ManualEarnRequest(
    @NotNull(message = "amount는 필수입니다.")
    @DecimalMin(value = "1", message = "amount는 1 이상이어야 합니다.")
    @Digits(integer = 15, fraction = 0, message = "amount는 소수 없이 최대 15자리여야 합니다.")
    BigDecimal amount,

    @Positive(message = "expireDays는 양수여야 합니다.")
    Integer expireDays,

    @NotBlank(message = "adminId는 필수입니다.")
    @Size(max = 64, message = "adminId는 64자 이하여야 합니다.")
    String adminId,

    @Size(max = 500, message = "reason은 500자 이하여야 합니다.")
    String reason
) {
}
