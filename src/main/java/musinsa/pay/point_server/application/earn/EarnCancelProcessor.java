package musinsa.pay.point_server.application.earn;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.application.earn.dto.EarnCancelRequest;
import musinsa.pay.point_server.application.earn.dto.EarnCancelResponse;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.common.generator.PointKeyGenerator;
import musinsa.pay.point_server.domain.account.PointBalance;
import musinsa.pay.point_server.domain.earn.EarnStatus;
import musinsa.pay.point_server.domain.earn.PointEarnCancel;
import musinsa.pay.point_server.domain.ledger.LedgerType;
import musinsa.pay.point_server.domain.ledger.PointLedger;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.persistence.account.PointBalanceRepository;
import musinsa.pay.point_server.persistence.earn.PointEarnCancelRepository;
import musinsa.pay.point_server.persistence.earn.PointEarnRepository;
import musinsa.pay.point_server.persistence.ledger.PointLedgerRepository;
import musinsa.pay.point_server.persistence.transaction.PointTransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 적립 취소 프로세서
 * - 적립 취소 트랜잭션 생성
 * - 적립 취소 내역 생성
 * - 적립 내역 상태 업데이트
 * - 계정 잔액 업데이트
 * - 원장 기록
 * - 취소 응답 조립
 */
@Component
@RequiredArgsConstructor
public class EarnCancelProcessor {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final EarnCancelContextFactory contextFactory;
    private final EarnCancelValidator validator;
    private final PointKeyGenerator pointKeyGenerator;
    private final PointTransactionRepository transactionRepository;
    private final PointEarnCancelRepository earnCancelRepository;
    private final PointEarnRepository earnRepository;
    private final PointBalanceRepository balanceRepository;
    private final PointLedgerRepository ledgerRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long cancel(
        EarnCancelRequest request,
        PointBalance balance
    ) {
        EarnCancelContext context = contextFactory.create(request, balance);
        validator.validate(context);

        EarnCancelCreateResult result = createEarnCancel(context);
        recordLedger(context, result);
        return result.transactionId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public EarnCancelResponse assemble(Long transactionId) {
        PointTransaction transaction = getTransaction(transactionId);
        PointEarnCancel earnCancel = getEarnCancel(transactionId);
        PointLedger ledger = getLedger(transactionId);
        PointTransaction earnTransaction = getTransaction(transaction.getRelatedTransactionId());
        return toResponse(transaction, earnCancel, ledger, earnTransaction);
    }

    private EarnCancelCreateResult createEarnCancel(EarnCancelContext context) {
        PointTransaction transaction = createTransaction(context);
        PointEarnCancel earnCancel = createEarnCancelHistory(context, transaction.getId());
        updateEarn(context);
        updateBalance(context);
        return EarnCancelCreateResult.from(transaction, earnCancel);
    }

    private PointTransaction createTransaction(EarnCancelContext context) {
        String pointKey = pointKeyGenerator.generate();
        return transactionRepository.save(context.toTransaction(pointKey));
    }

    private PointEarnCancel createEarnCancelHistory(
        EarnCancelContext context,
        Long transactionId
    ) {
        return earnCancelRepository.save(context.toEarnCancel(transactionId));
    }

    private void updateEarn(EarnCancelContext context) {
        int updatedRows = earnRepository.cancelEarn(
            context.earn().getId(),
            context.earn().getAccountId(),
            ZERO,
            context.cancelAmount(),
            EarnStatus.CANCELED
        );
        if (updatedRows != 1) {
            throw new BaseException(ErrorCode.POINT_EARN_CANCEL_NOT_ALLOWED);
        }
    }

    private void updateBalance(EarnCancelContext context) {
        int updatedRows = balanceRepository.updateBalanceAmount(
            context.earn().getAccountId(),
            context.balanceAfter()
        );
        if (updatedRows != 1) {
            throw new BaseException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    private void recordLedger(
        EarnCancelContext context,
        EarnCancelCreateResult result
    ) {
        ledgerRepository.save(context.toLedger(result));
    }

    private PointTransaction getTransaction(Long transactionId) {
        return transactionRepository.findById(transactionId)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_TRANSACTION_NOT_FOUND));
    }

    private PointEarnCancel getEarnCancel(Long transactionId) {
        return earnCancelRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_EARN_CANCEL_NOT_FOUND));
    }

    private PointLedger getLedger(Long transactionId) {
        return ledgerRepository.findFirstByTransactionIdAndLedgerType(
                transactionId,
                LedgerType.EARN_CANCEL_DECREASE
            )
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_LEDGER_NOT_FOUND));
    }

    private EarnCancelResponse toResponse(
        PointTransaction transaction,
        PointEarnCancel earnCancel,
        PointLedger ledger,
        PointTransaction earnTransaction
    ) {
        return new EarnCancelResponse(
            transaction.getPointKey(),
            transaction.getId(),
            earnCancel.getId(),
            transaction.getAccountId(),
            earnTransaction.getPointKey(),
            earnCancel.getEarnId(),
            transaction.getAmount(),
            ledger.getAccountBalanceBefore(),
            ledger.getAccountBalanceAfter()
        );
    }
}
