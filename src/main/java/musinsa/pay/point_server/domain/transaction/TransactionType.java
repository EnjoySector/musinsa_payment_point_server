package musinsa.pay.point_server.domain.transaction;

public enum TransactionType {
    EARN,          // 포인트 적립
    EARN_CANCEL,   // 포인트 적립 취소
    USE,            // 포인트 사용
    USE_CANCEL,     // 포인트 사용 취소
    EXPIRE          // 포인트 만료
}