package musinsa.pay.point_server.application.earn;

import java.math.BigDecimal;
import musinsa.pay.point_server.application.earn.dto.EarnCancelRequest;
import musinsa.pay.point_server.domain.earn.PointEarn;
import musinsa.pay.point_server.domain.earn.PointEarnCancel;
import musinsa.pay.point_server.domain.ledger.LedgerType;
import musinsa.pay.point_server.domain.ledger.PointLedger;
import musinsa.pay.point_server.domain.transaction.CreatedByType;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.transaction.TransactionType;

/**
 * 적립취소 처리 중 필요한 거래/적립/잔액 계산 결과 처리 컨텍스트
 */
public record EarnCancelContext(
    EarnCancelRequest request,
    PointTransaction earnTransaction,
    PointEarn earn,
    BigDecimal cancelAmount,
    BigDecimal balanceBefore,
    BigDecimal balanceAfter,
    CreatedByType createdByType,
    String createdById
) {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public PointTransaction toTransaction(String pointKey) {
        return PointTransaction.builder()
            .pointKey(pointKey)
            .accountId(earn.getAccountId())
            .pointPolicyId(earnTransaction.getPointPolicyId())
            .pointUserPolicyId(earnTransaction.getPointUserPolicyId())
            .transactionType(TransactionType.EARN_CANCEL)
            .amount(cancelAmount)
            .relatedTransactionId(earnTransaction.getId())
            .createdByType(createdByType)
            .createdById(createdById)
            .reason(request.reason())
            .build();
    }

    public PointEarnCancel toEarnCancel(Long transactionId) {
        return PointEarnCancel.builder()
            .transactionId(transactionId)
            .accountId(earn.getAccountId())
            .earnId(earn.getId())
            .cancelAmount(cancelAmount)
            .build();
    }

    public PointLedger toLedger(EarnCancelCreateResult result) {
        return PointLedger.builder()
            .transactionId(result.transactionId())
            .accountId(earn.getAccountId())
            .earnId(earn.getId())
            .ledgerType(LedgerType.EARN_CANCEL_DECREASE)
            .deltaAmount(cancelAmount.negate())
            .accountBalanceBefore(balanceBefore)
            .accountBalanceAfter(balanceAfter)
            .earnAvailableBefore(cancelAmount)
            .earnAvailableAfter(ZERO)
            .description("포인트 적립취소")
            .build();
    }
}
