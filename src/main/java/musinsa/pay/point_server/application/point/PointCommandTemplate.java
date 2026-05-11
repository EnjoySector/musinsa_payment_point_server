package musinsa.pay.point_server.application.point;

import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.application.expire.ExpirePointProcessor;
import musinsa.pay.point_server.application.expire.ExpirePointResult;
import musinsa.pay.point_server.application.idempotency.IdempotencyService;
import musinsa.pay.point_server.application.idempotency.IdempotentOperation;
import musinsa.pay.point_server.application.idempotency.IdempotentRequest;
import musinsa.pay.point_server.domain.account.PointBalance;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 변경 요청 공통 실행 흐름.
 *
 * 멱등성 재호출이면 기존 거래를 반환하고,
 * 새 요청이면 만료 정리 후 최신 잔액으로 실제 처리를 실행한다.
 */
@Component
@RequiredArgsConstructor
public class PointCommandTemplate {

    private final IdempotencyService idempotencyService;
    private final ExpirePointProcessor expirePointProcessor;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long execute(
        IdempotentRequest request,
        IdempotentOperation operation
    ) {
        return idempotencyService.executeOrReturn(
            request,
            balance -> {
                ExpirePointResult expiration = expirePointProcessor.expire(balance);
                return operation.execute(currentBalance(balance, expiration));
            }
        );
    }

    private PointBalance currentBalance(
        PointBalance lockedBalance,
        ExpirePointResult expiration
    ) {
        if (expiration.expiredAmount().signum() == 0) {
            return lockedBalance;
        }
        return PointBalance.builder()
            .accountId(lockedBalance.getAccountId())
            .balanceAmount(expiration.balanceAfter())
            .build();
    }
}
