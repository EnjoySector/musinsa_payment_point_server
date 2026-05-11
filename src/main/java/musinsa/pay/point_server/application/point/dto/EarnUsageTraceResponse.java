package musinsa.pay.point_server.application.point.dto;

import java.math.BigDecimal;
import java.util.List;
import musinsa.pay.point_server.domain.earn.EarnStatus;
import musinsa.pay.point_server.domain.earn.EarnType;
import musinsa.pay.point_server.domain.earn.PointEarn;
import musinsa.pay.point_server.domain.transaction.PointTransaction;

public record EarnUsageTraceResponse(
    String earnPointKey,
    Long earnId,
    EarnType earnType,
    EarnStatus status,
    BigDecimal earnAmount,
    BigDecimal availableAmount,
    List<EarnUsageAllocationResponse> usages
) {

    public static EarnUsageTraceResponse of(
        PointTransaction transaction,
        PointEarn earn,
        List<EarnUsageAllocationResponse> usages
    ) {
        return new EarnUsageTraceResponse(
            transaction.getPointKey(),
            earn.getId(),
            earn.getEarnType(),
            earn.getStatus(),
            earn.getEarnAmount(),
            earn.getAvailableAmount(),
            usages
        );
    }
}
