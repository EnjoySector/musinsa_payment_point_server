package musinsa.pay.point_server.application.point.dto;

import java.math.BigDecimal;
import musinsa.pay.point_server.domain.account.AccountStatus;
import musinsa.pay.point_server.domain.account.PointAccount;
import musinsa.pay.point_server.domain.account.PointBalance;

public record PointSummaryResponse(
    Long accountId,
    Long userId,
    AccountStatus accountStatus,
    BigDecimal balanceAmount,
    BigDecimal normalAvailableAmount,
    BigDecimal manualAvailableAmount,
    BigDecimal restoredAvailableAmount,
    UserPolicySummary userPolicy
) {

    public static PointSummaryResponse of(
        PointAccount account,
        PointBalance balance,
        BigDecimal normalAvailableAmount,
        BigDecimal manualAvailableAmount,
        BigDecimal restoredAvailableAmount,
        UserPolicySummary userPolicy
    ) {
        return new PointSummaryResponse(
            account.getId(),
            account.getUserId(),
            account.getStatus(),
            balance.getBalanceAmount(),
            normalAvailableAmount,
            manualAvailableAmount,
            restoredAvailableAmount,
            userPolicy
        );
    }
}
