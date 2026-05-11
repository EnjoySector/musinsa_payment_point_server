package musinsa.pay.point_server.application.earn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 포인트 적립취소 요청.
 */
public record EarnCancelRequest(
    @NotNull(message = "accountId는 필수입니다.")
    @Positive(message = "accountId는 양수여야 합니다.")
    Long accountId,

    @NotBlank(message = "earnPointKey는 필수입니다.")
    @Size(max = 64, message = "earnPointKey는 64자 이하여야 합니다.")
    String earnPointKey,

    @Size(max = 500, message = "reason은 500자 이하여야 합니다.")
    String reason
) {
}
