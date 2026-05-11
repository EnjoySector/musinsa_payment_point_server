package musinsa.pay.point_server.application.earn;

import musinsa.pay.point_server.domain.account.PointAccount;
import musinsa.pay.point_server.domain.earn.EarnStatus;
import musinsa.pay.point_server.domain.earn.EarnType;
import musinsa.pay.point_server.domain.earn.PointEarn;
import musinsa.pay.point_server.domain.ledger.LedgerType;
import musinsa.pay.point_server.domain.ledger.PointLedger;
import musinsa.pay.point_server.domain.policy.PointPolicy;
import musinsa.pay.point_server.domain.policy.PointUserPolicy;
import musinsa.pay.point_server.domain.transaction.CreatedByType;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 적립 처리 중 필요한 계정, 정책, 금액 계산 결과 처리 컨텍스트
 * 거래/적립/원장 생성 객체 변환 책임 포함
 */
public record EarnPointContext(
    EarnPointCommand command,
    PointAccount account,
    PointPolicy pointPolicy,
    PointUserPolicy userPolicy,
    BigDecimal amount,
    EarnType earnType,
    CreatedByType createdByType,
    String createdById,
    LocalDateTime expiresAt,
    BigDecimal balanceBefore,
    BigDecimal balanceAfter
) {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public PointTransaction toTransaction(String pointKey) {
        return PointTransaction.builder()
            .pointKey(pointKey)
            .accountId(account.getId())
            .pointPolicyId(pointPolicy.getId())
            .pointUserPolicyId(userPolicy.getId())
            .transactionType(TransactionType.EARN)
            .amount(amount)
            .createdByType(createdByType)
            .createdById(createdById)
            .reason(command.reason())
            .build();
    }

    public PointEarn toEarn(Long transactionId) {
        return PointEarn.builder()
            .transactionId(transactionId)
            .accountId(account.getId())
            .earnType(earnType)
            .earnAmount(amount)
            .availableAmount(amount)
            .consumedAmount(ZERO)
            .cancelledAmount(ZERO)
            .expiredAmount(ZERO)
            .expiresAt(expiresAt)
            .status(EarnStatus.AVAILABLE)
            .build();
    }

    public PointLedger toLedger(EarnPointCreateResult result) {
        return PointLedger.builder()
            .transactionId(result.transactionId())
            .accountId(account.getId())
            .earnId(result.earnId())
            .ledgerType(LedgerType.EARN_INCREASE)
            .deltaAmount(amount)
            .accountBalanceBefore(balanceBefore)
            .accountBalanceAfter(balanceAfter)
            .earnAvailableBefore(ZERO)
            .earnAvailableAfter(amount)
            .description("포인트 적립")
            .build();
    }
}
