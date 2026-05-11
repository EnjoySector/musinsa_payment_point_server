package musinsa.pay.point_server.domain.usage;

public enum UsageStatus {
    USED,              // 사용됨
    PARTIAL_CANCELED,  // 부분 취소됨
    CANCELED           // 전액 취소됨
}