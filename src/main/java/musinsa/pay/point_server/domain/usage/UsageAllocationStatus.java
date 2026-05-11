package musinsa.pay.point_server.domain.usage;

public enum UsageAllocationStatus {
    USED,               // 사용 완료
    PARTIAL_CANCELED,   // 부분 취소
    CANCELED            // 전체 취소
}
