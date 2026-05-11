package musinsa.pay.point_server.domain.ledger;

public enum LedgerType {
    EARN_INCREASE,                    // 적립 → 잔액 증가
    EARN_CANCEL_DECREASE,             // 적립취소 → 잔액 감소
    USE_DECREASE,                     // 사용 → 잔액 감소
    USE_CANCEL_ORIGINAL_INCREASE,     // 사용취소: 원래 적립 복구 → 잔액 증가
    USE_CANCEL_NEW_EARN_INCREASE,     // 사용취소: 만료분 신규 적립 → 잔액 증가
    EXPIRE_DECREASE                   // 만료 → 잔액 감소
}