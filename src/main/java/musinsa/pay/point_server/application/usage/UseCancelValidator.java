package musinsa.pay.point_server.application.usage;

import java.math.BigDecimal;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.usage.UsageStatus;
import org.springframework.stereotype.Component;

/**
 * 포인트 사용취소 금액, 취소 가능 상태 검증
 */
@Component
public class UseCancelValidator {

    private static final BigDecimal MIN_CANCEL_AMOUNT = BigDecimal.ONE;

    public void validate(UseCancelContext context) {
        validateAmount(context.cancelAmount());
        validateUsageStatus(context);
        validateCancelableAmount(context);
    }

    private void validateAmount(BigDecimal cancelAmount) {
        if (cancelAmount.compareTo(MIN_CANCEL_AMOUNT) < 0) {
            throw new BaseException(ErrorCode.POINT_AMOUNT_INVALID);
        }
    }

    private void validateUsageStatus(UseCancelContext context) {
        if (context.usage().getStatus() == UsageStatus.CANCELED) {
            throw new BaseException(ErrorCode.POINT_USAGE_ALREADY_CANCELED);
        }
    }

    private void validateCancelableAmount(UseCancelContext context) {
        BigDecimal cancelableAmount = context.usage().getUsageAmount()
            .subtract(context.usage().getCancelledAmount());
        if (context.cancelAmount().compareTo(cancelableAmount) > 0) {
            throw new BaseException(ErrorCode.POINT_USAGE_CANCEL_AMOUNT_EXCEEDED);
        }
    }
}
