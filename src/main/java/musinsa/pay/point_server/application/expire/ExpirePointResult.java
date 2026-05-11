package musinsa.pay.point_server.application.expire;

import java.math.BigDecimal;
import java.util.List;

/**
 * 만료 처리 결과와 만료된 적립별 잔액 변화 정보.
 */
public record ExpirePointResult(
    Long accountId,
    BigDecimal balanceBefore,
    BigDecimal balanceAfter,
    BigDecimal expiredAmount,
    List<ExpiredEarnResult> expiredEarns
) {

    public static ExpirePointResult empty(
        Long accountId,
        BigDecimal balanceAmount
    ) {
        return new ExpirePointResult(
            accountId,
            balanceAmount,
            balanceAmount,
            BigDecimal.ZERO,
            List.of()
        );
    }

    public int expiredCount() {
        return expiredEarns.size();
    }

    public record ExpiredEarnResult(
        Long earnId,
        Long transactionId,
        String pointKey,
        BigDecimal expiredAmount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter
    ) {
    }
}
