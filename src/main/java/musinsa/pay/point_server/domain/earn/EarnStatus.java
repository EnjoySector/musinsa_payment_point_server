package musinsa.pay.point_server.domain.earn;

public enum EarnStatus {
    AVAILABLE,  // 사용 가능
    EXHAUSTED,  // 소진
    CANCELED,   // 취소
    EXPIRED     // 만료
}