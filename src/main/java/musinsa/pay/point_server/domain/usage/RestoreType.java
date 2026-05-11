package musinsa.pay.point_server.domain.usage;

public enum RestoreType {
    ORIGINAL_EARN,   // 원래 적립이 만료 안 됨 → 복구
    NEW_EARN         // 원래 적립이 만료됨 → 신규 적립 생성
}
