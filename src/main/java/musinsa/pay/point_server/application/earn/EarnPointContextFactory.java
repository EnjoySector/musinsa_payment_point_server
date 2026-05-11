package musinsa.pay.point_server.application.earn;

import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.account.AccountStatus;
import musinsa.pay.point_server.domain.account.PointAccount;
import musinsa.pay.point_server.domain.account.PointBalance;
import musinsa.pay.point_server.domain.earn.EarnType;
import musinsa.pay.point_server.domain.policy.PointPolicy;
import musinsa.pay.point_server.domain.policy.PointUserPolicy;
import musinsa.pay.point_server.domain.policy.PolicyStatus;
import musinsa.pay.point_server.persistence.account.PointAccountRepository;
import musinsa.pay.point_server.persistence.policy.PointPolicyRepository;
import musinsa.pay.point_server.persistence.policy.PointUserPolicyRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 적립 처리 컨텍스트 팩토리
 * - 계정, 정책 조회 및 검증
 */
@Component
@RequiredArgsConstructor
public class EarnPointContextFactory {

    private static final String DEFAULT_POINT_POLICY_CODE = "DEFAULT";

    private final PointAccountRepository accountRepository;
    private final PointPolicyRepository pointPolicyRepository;
    private final PointUserPolicyRepository pointUserPolicyRepository;

    public EarnPointContext create(
        EarnPointCommand command,
        PointBalance balance
    ) {
        PointAccount account = getActiveAccount(command.accountId());
        PointPolicy pointPolicy = getActivePointPolicy();
        PointUserPolicy userPolicy = getActiveUserPolicy(account.getPointUserPolicyId());
        return createContext(command, balance, account, pointPolicy, userPolicy);
    }

    private EarnPointContext createContext(
        EarnPointCommand command,
        PointBalance balance,
        PointAccount account,
        PointPolicy pointPolicy,
        PointUserPolicy userPolicy
    ) {
        BigDecimal amount = normalizeAmount(command.amount());
        BigDecimal balanceAfter = balance.getBalanceAmount().add(amount);
        return new EarnPointContext(
            command,
            account,
            pointPolicy,
            userPolicy,
            amount,
            resolveEarnType(command.earnType()),
            command.createdByType(),
            resolveCreatedById(command, account),
            resolveExpiresAt(command.expireDays(), pointPolicy),
            balance.getBalanceAmount(),
            balanceAfter
        );
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

    private PointPolicy getActivePointPolicy() {
        return pointPolicyRepository.findByPolicyCodeAndStatus(DEFAULT_POINT_POLICY_CODE, PolicyStatus.ACTIVE)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_POLICY_NOT_FOUND));
    }

    private PointUserPolicy getActiveUserPolicy(Long userPolicyId) {
        PointUserPolicy userPolicy = pointUserPolicyRepository.findById(userPolicyId)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_USER_POLICY_NOT_FOUND));
        validateUserPolicyStatus(userPolicy);
        return userPolicy;
    }

    private void validateUserPolicyStatus(PointUserPolicy userPolicy) {
        if (userPolicy.getStatus() != PolicyStatus.ACTIVE) {
            throw new BaseException(ErrorCode.POINT_POLICY_INACTIVE);
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        try {
            return amount.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new BaseException(ErrorCode.POINT_AMOUNT_INVALID);
        }
    }

    private EarnType resolveEarnType(EarnType earnType) {
        return earnType == null ? EarnType.NORMAL : earnType;
    }

    private String resolveCreatedById(
        EarnPointCommand command,
        PointAccount account
    ) {
        if (StringUtils.hasText(command.createdById())) {
            return command.createdById();
        }
        return String.valueOf(account.getUserId());
    }

    private LocalDateTime resolveExpiresAt(
        Integer requestedExpireDays,
        PointPolicy pointPolicy
    ) {
        int expireDays = requestedExpireDays == null
            ? pointPolicy.getDefaultExpireDays()
            : requestedExpireDays;
        validateExpireDays(expireDays, pointPolicy);
        return LocalDateTime.now().plusDays(expireDays);
    }

    private void validateExpireDays(
        Integer expireDays,
        PointPolicy pointPolicy
    ) {
        if (expireDays < pointPolicy.getMinExpireDays() || expireDays > pointPolicy.getMaxExpireDays()) {
            throw new BaseException(ErrorCode.POINT_EXPIRE_DAYS_INVALID);
        }
    }
}
