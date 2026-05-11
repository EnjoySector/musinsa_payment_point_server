package musinsa.pay.point_server.application.usage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.application.usage.dto.UsePointRequest;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.account.AccountStatus;
import musinsa.pay.point_server.domain.account.PointAccount;
import musinsa.pay.point_server.domain.account.PointBalance;
import musinsa.pay.point_server.domain.transaction.CreatedByType;
import musinsa.pay.point_server.persistence.account.PointAccountRepository;
import org.springframework.stereotype.Component;

/**
 * 포인트 사용 처리 컨텍스트 생성
 * 계정 조회, 금액 기본값 계산
 */
@Component
@RequiredArgsConstructor
public class UsePointContextFactory {

    private final PointAccountRepository accountRepository;

    public UsePointContext create(UsePointRequest request, PointBalance balance) {
        PointAccount account = getActiveAccount(request.accountId());
        BigDecimal amount = normalizeAmount(request.amount());
        return createContext(request, balance, account, amount);
    }

    private PointAccount getActiveAccount(Long accountId) {
        PointAccount account = accountRepository.findById(accountId)
            .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_FOUND));
        validateAccountStatus(account);
        return account;
    }

    private void validateAccountStatus(PointAccount account) {
        if (account.getStatus() == AccountStatus.BLOCKED) {
            throw new BaseException(ErrorCode.ACCOUNT_BLOCKED);
        }
        if (account.getStatus() == AccountStatus.DELETED) {
            throw new BaseException(ErrorCode.ACCOUNT_DELETED);
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        try {
            return amount.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new BaseException(ErrorCode.POINT_AMOUNT_INVALID);
        }
    }

    private UsePointContext createContext(
        UsePointRequest request,
        PointBalance balance,
        PointAccount account,
        BigDecimal amount
    ) {
        return new UsePointContext(
            request,
            account,
            amount,
            CreatedByType.USER,
            String.valueOf(account.getUserId()),
            balance.getBalanceAmount(),
            balance.getBalanceAmount().subtract(amount)
        );
    }
}
