package musinsa.pay.point_server.application.earn;

import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.application.earn.dto.EarnPointRequest;
import musinsa.pay.point_server.application.earn.dto.EarnPointResponse;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.common.generator.PointKeyGenerator;
import musinsa.pay.point_server.domain.account.PointBalance;
import musinsa.pay.point_server.domain.earn.PointEarn;
import musinsa.pay.point_server.domain.ledger.LedgerType;
import musinsa.pay.point_server.domain.ledger.PointLedger;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.persistence.account.PointBalanceRepository;
import musinsa.pay.point_server.persistence.earn.PointEarnRepository;
import musinsa.pay.point_server.persistence.ledger.PointLedgerRepository;
import musinsa.pay.point_server.persistence.transaction.PointTransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 적립 프로세서
 * - 적립 트랜잭션 생성
 * - 적립 내역 생성
 * - 계정 잔액 업데이트
 * - 원장 기록
 * - 적립 응답 조립
 */
@Component
@RequiredArgsConstructor
public class EarnPointProcessor {

    private final EarnPointContextFactory contextFactory;
    private final EarnPointValidator validator;
    private final PointKeyGenerator pointKeyGenerator;
    private final PointTransactionRepository transactionRepository;
    private final PointEarnRepository earnRepository;
    private final PointBalanceRepository balanceRepository;
    private final PointLedgerRepository ledgerRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long earn(
        EarnPointCommand command,
        PointBalance balance
    ) {
        EarnPointContext context = contextFactory.create(command, balance);
        validator.validate(context);

        EarnPointCreateResult result = createEarn(context);
        recordLedger(context, result);
        return result.transactionId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public EarnPointResponse assemble(Long transactionId) {
        PointTransaction transaction = getTransaction(transactionId);
        PointEarn earn = getEarn(transactionId);
        PointLedger ledger = getLedger(transactionId);
        return toResponse(transaction, earn, ledger);
    }

    private EarnPointCreateResult createEarn(EarnPointContext context) {
        PointTransaction transaction = createTransaction(context);
        PointEarn earn = createEarnHistory(context, transaction.getId());
        updateBalance(context);
        return EarnPointCreateResult.from(transaction, earn);
    }

    private PointTransaction createTransaction(EarnPointContext context) {
        String pointKey = pointKeyGenerator.generate();
        return transactionRepository.save(context.toTransaction(pointKey));
    }

    private PointEarn createEarnHistory(
        EarnPointContext context,
        Long transactionId
    ) {
        return earnRepository.save(context.toEarn(transactionId));
    }

    private void updateBalance(EarnPointContext context) {
        int updatedRows = balanceRepository.updateBalanceAmount(
            context.account().getId(),
            context.balanceAfter()
        );
        if (updatedRows != 1) {
            throw new BaseException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    private void recordLedger(
        EarnPointContext context,
        EarnPointCreateResult result
    ) {
        PointLedger ledger = context.toLedger(result);
        ledgerRepository.save(ledger);
    }

    private PointTransaction getTransaction(Long transactionId) {
        return transactionRepository.findById(transactionId)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_TRANSACTION_NOT_FOUND));
    }

    private PointEarn getEarn(Long transactionId) {
        return earnRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_EARN_NOT_FOUND));
    }

    private PointLedger getLedger(Long transactionId) {
        return ledgerRepository.findFirstByTransactionIdAndLedgerType(
                transactionId,
                LedgerType.EARN_INCREASE
            )
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_LEDGER_NOT_FOUND));
    }

    private EarnPointResponse toResponse(
        PointTransaction transaction,
        PointEarn earn,
        PointLedger ledger
    ) {
        return new EarnPointResponse(
            transaction.getPointKey(),
            transaction.getId(),
            earn.getId(),
            transaction.getAccountId(),
            earn.getEarnType(),
            transaction.getAmount(),
            ledger.getAccountBalanceBefore(),
            ledger.getAccountBalanceAfter(),
            earn.getExpiresAt()
        );
    }
}
