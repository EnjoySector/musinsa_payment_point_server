package musinsa.pay.point_server.application.idempotency;

import musinsa.pay.point_server.domain.account.PointBalance;

/**
 * 멱등성 보장 대상 로직
 * 잠긴 잔액 기반 transaction id 생성
 */
@FunctionalInterface
public interface IdempotentOperation {
    Long execute(PointBalance balance);
}
