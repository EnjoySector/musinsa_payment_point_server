package musinsa.pay.point_server.application.earn;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.earn.EarnStatus;
import musinsa.pay.point_server.domain.earn.PointEarn;
import org.springframework.stereotype.Component;

/**
 * 적립취소 가능 상태 검증.
 */
@Component
public class EarnCancelValidator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public void validate(EarnCancelContext context) {
        validateEarnCancelable(context.earn());
        validateBalance(context.balanceAfter());
    }

    private void validateEarnCancelable(PointEarn earn) {
        validateEarnStatus(earn);
        if (!earn.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BaseException(ErrorCode.POINT_EARN_EXPIRED);
        }
        if (!isFullAmountRemaining(earn)) {
            throw new BaseException(ErrorCode.POINT_EARN_ALREADY_USED);
        }
    }

    private void validateEarnStatus(PointEarn earn) {
        if (earn.getStatus() == EarnStatus.CANCELED) {
            throw new BaseException(ErrorCode.POINT_EARN_ALREADY_CANCELED);
        }
        if (earn.getStatus() == EarnStatus.EXPIRED) {
            throw new BaseException(ErrorCode.POINT_EARN_EXPIRED);
        }
        if (earn.getStatus() == EarnStatus.EXHAUSTED) {
            throw new BaseException(ErrorCode.POINT_EARN_ALREADY_USED);
        }
    }

    private boolean isFullAmountRemaining(PointEarn earn) {
        return earn.getAvailableAmount().compareTo(earn.getEarnAmount()) == 0
            && earn.getConsumedAmount().compareTo(ZERO) == 0
            && earn.getCancelledAmount().compareTo(ZERO) == 0
            && earn.getExpiredAmount().compareTo(ZERO) == 0;
    }

    private void validateBalance(BigDecimal balanceAfter) {
        if (balanceAfter.compareTo(ZERO) < 0) {
            throw new BaseException(ErrorCode.POINT_EARN_CANCEL_BALANCE_INSUFFICIENT);
        }
    }
}
