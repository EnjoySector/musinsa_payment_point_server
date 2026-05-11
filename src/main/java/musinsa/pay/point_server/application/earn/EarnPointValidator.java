package musinsa.pay.point_server.application.earn;

import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.earn.EarnType;
import musinsa.pay.point_server.domain.transaction.CreatedByType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 적립 금액, 보유 한도, 수기 지급 규칙 검증
 */
@Component
public class EarnPointValidator {

    private static final BigDecimal MIN_EARN_AMOUNT = BigDecimal.ONE;

    public void validate(EarnPointContext context) {
        validateEarnType(context.earnType(), context.createdByType());
        validateEarnAmount(context);
        validateBalanceLimit(context);
    }

    private void validateEarnType(EarnType earnType, CreatedByType createdByType) {
        if (earnType == EarnType.USE_CANCEL_RESTORE) {
            throw new BaseException(ErrorCode.POINT_EARN_TYPE_INVALID);
        }
        if (earnType == EarnType.MANUAL && createdByType != CreatedByType.ADMIN) {
            throw new BaseException(ErrorCode.POINT_MANUAL_EARN_FORBIDDEN);
        }
    }

    private void validateEarnAmount(EarnPointContext context) {
        if (context.amount().compareTo(MIN_EARN_AMOUNT) < 0) {
            throw new BaseException(ErrorCode.POINT_AMOUNT_INVALID);
        }
        if (context.amount().compareTo(context.pointPolicy().getMaxEarnAmount()) > 0) {
            throw new BaseException(ErrorCode.POINT_EARN_AMOUNT_EXCEEDED);
        }
    }

    private void validateBalanceLimit(EarnPointContext context) {
        if (context.balanceAfter().compareTo(context.userPolicy().getMaxBalanceAmount()) > 0) {
            throw new BaseException(ErrorCode.POINT_BALANCE_LIMIT_EXCEEDED);
        }
    }
}
