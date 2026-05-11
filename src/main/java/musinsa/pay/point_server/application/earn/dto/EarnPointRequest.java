package musinsa.pay.point_server.application.earn.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import musinsa.pay.point_server.domain.earn.EarnType;

/**
 * 포인트 적립 요청.
 */
public record EarnPointRequest(
    @NotNull(message = "accountId는 필수입니다.")
    @Positive(message = "accountId는 양수여야 합니다.")
    Long accountId,

    @NotNull(message = "amount는 필수입니다.")
    @DecimalMin(value = "1", message = "amount는 1 이상이어야 합니다.")
    @Digits(integer = 15, fraction = 0, message = "amount는 소수 없이 최대 15자리여야 합니다.")
    BigDecimal amount,

    EarnType earnType,

    @Positive(message = "expireDays는 양수여야 합니다.")
    Integer expireDays,

    @Size(max = 500, message = "reason은 500자 이하여야 합니다.")
    String reason
) {
}
