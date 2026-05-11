package musinsa.pay.point_server.application.earn;

import java.math.BigDecimal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.application.earn.dto.EarnCancelRequest;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.account.AccountStatus;
import musinsa.pay.point_server.domain.account.PointAccount;
import musinsa.pay.point_server.domain.account.PointBalance;
import musinsa.pay.point_server.domain.earn.PointEarn;
import musinsa.pay.point_server.domain.transaction.CreatedByType;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import musinsa.pay.point_server.persistence.account.PointAccountRepository;
import musinsa.pay.point_server.persistence.earn.PointEarnRepository;
import musinsa.pay.point_server.persistence.transaction.PointTransactionRepository;
import org.springframework.stereotype.Component;

/**
 * 포인트 적립 취소 컨텍스트 팩토리
 * - 계정, 적립 트랜잭션, 적립 내역 조회 및 검증
 * - 취소 컨텍스트 생성
 */
@Component
@RequiredArgsConstructor
public class EarnCancelContextFactory {

    private final PointAccountRepository accountRepository;
    private final PointTransactionRepository transactionRepository;
    private final PointEarnRepository earnRepository;

    public EarnCancelContext create(
        EarnCancelRequest request,
        PointBalance balance
    ) {
        PointAccount account = getActiveAccount(request.accountId());
        PointTransaction earnTransaction = getEarnTransaction(request.earnPointKey());
        validateTransactionOwner(request.accountId(), earnTransaction);
        PointEarn earn = getEarn(earnTransaction.getId());
        return createContext(request, balance, account, earnTransaction, earn);
    }

    private PointAccount getActiveAccount(Long accountId) {
        PointAccount account = accountRepository.findById(accountId)
            .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (account.getStatus() == AccountStatus.BLOCKED) {
            throw new BaseException(ErrorCode.ACCOUNT_BLOCKED);
        }
        if (account.getStatus() == AccountStatus.DELETED) {
            throw new BaseException(ErrorCode.ACCOUNT_DELETED);
        }
        return account;
    }

    private PointTransaction getEarnTransaction(String earnPointKey) {
        return transactionRepository
            .findByPointKeyAndTransactionType(earnPointKey, TransactionType.EARN)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_TRANSACTION_NOT_FOUND));
    }

    private void validateTransactionOwner(
        Long accountId,
        PointTransaction earnTransaction
    ) {
        if (!Objects.equals(accountId, earnTransaction.getAccountId())) {
            throw new BaseException(ErrorCode.POINT_EARN_CANCEL_NOT_ALLOWED);
        }
    }

    private PointEarn getEarn(Long earnTransactionId) {
        return earnRepository.findByTransactionIdForUpdate(earnTransactionId)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_EARN_NOT_FOUND));
    }

    private EarnCancelContext createContext(
        EarnCancelRequest request,
        PointBalance balance,
        PointAccount account,
        PointTransaction earnTransaction,
        PointEarn earn
    ) {
        BigDecimal cancelAmount = earn.getEarnAmount();
        return new EarnCancelContext(
            request,
            earnTransaction,
            earn,
            cancelAmount,
            balance.getBalanceAmount(),
            balance.getBalanceAmount().subtract(cancelAmount),
            CreatedByType.USER,
            String.valueOf(account.getUserId())
        );
    }
}
