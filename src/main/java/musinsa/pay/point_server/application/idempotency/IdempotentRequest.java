package musinsa.pay.point_server.application.idempotency;

import java.util.Objects;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import org.springframework.util.StringUtils;

/**
 * 멱등성 처리에 필요한 요청 식별 정보.
 */
public record IdempotentRequest(
    Long accountId,
    TransactionType requestType,
    String idempotencyKey,
    Object body
) {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    public IdempotentRequest {
        Objects.requireNonNull(accountId, "계정 ID는 필수입니다.");
        Objects.requireNonNull(requestType, "요청 유형은 필수입니다.");
        Objects.requireNonNull(body, "요청 본문은 필수입니다.");

        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BaseException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }

        idempotencyKey = idempotencyKey.trim();
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BaseException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
    }
}
