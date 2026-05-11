package musinsa.pay.point_server.application.usage;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.persistence.usage.PointUsageRepository;
import org.springframework.stereotype.Component;

/**
 * 포인트 사용 금액, 주문 중복, 잔액 검증
 */
@Component
@RequiredArgsConstructor
public class UsePointValidator {

    private static final BigDecimal MIN_USE_AMOUNT = BigDecimal.ONE;

    private final PointUsageRepository usageRepository;

    public void validate(UsePointContext context) {
        validateAmount(context.amount());
        validateBalance(context.balanceAfter());
        validateOrder(context.account().getId(), context.request().orderNo());
    }

    private void validateAmount(BigDecimal amount) {
        if (amount.compareTo(MIN_USE_AMOUNT) < 0) {
            throw new BaseException(ErrorCode.POINT_AMOUNT_INVALID);
        }
    }

    private void validateBalance(BigDecimal balanceAfter) {
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BaseException(ErrorCode.POINT_BALANCE_NOT_ENOUGH);
        }
    }

    private void validateOrder(
        Long accountId,
        String orderNo
    ) {
        if (usageRepository.existsByAccountIdAndOrderNo(accountId, orderNo)) {
            throw new BaseException(ErrorCode.POINT_USAGE_DUPLICATE_ORDER);
        }
    }
}
