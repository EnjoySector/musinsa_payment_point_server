package musinsa.pay.point_server.application.usage.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 포인트 사용취소 요청.
 */
public record UseCancelRequest(
    @NotNull(message = "accountId는 필수입니다.")
    @Positive(message = "accountId는 양수여야 합니다.")
    Long accountId,

    @NotBlank(message = "usePointKey는 필수입니다.")
    @Size(max = 64, message = "usePointKey는 64자 이하여야 합니다.")
    String usePointKey,

    @NotNull(message = "cancelAmount는 필수입니다.")
    @DecimalMin(value = "1", message = "cancelAmount는 1 이상이어야 합니다.")
    @Digits(integer = 15, fraction = 0, message = "cancelAmount는 소수 없이 최대 15자리여야 합니다.")
    BigDecimal cancelAmount,

    @Size(max = 500, message = "reason은 500자 이하여야 합니다.")
    String reason
) {
}
