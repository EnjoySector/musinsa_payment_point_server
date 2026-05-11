package musinsa.pay.point_server.application.earn;

import static musinsa.pay.point_server.support.EntityTestSupport.persisted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import musinsa.pay.point_server.application.earn.dto.EarnPointRequest;
import musinsa.pay.point_server.application.earn.dto.EarnPointResponse;
import musinsa.pay.point_server.application.idempotency.IdempotentOperation;
import musinsa.pay.point_server.application.point.PointCommandTemplate;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.common.generator.PointKeyGenerator;
import musinsa.pay.point_server.domain.account.AccountStatus;
import musinsa.pay.point_server.domain.account.PointAccount;
import musinsa.pay.point_server.domain.account.PointBalance;
import musinsa.pay.point_server.domain.earn.EarnStatus;
import musinsa.pay.point_server.domain.earn.EarnType;
import musinsa.pay.point_server.domain.earn.PointEarn;
import musinsa.pay.point_server.domain.ledger.LedgerType;
import musinsa.pay.point_server.domain.ledger.PointLedger;
import musinsa.pay.point_server.domain.policy.PointPolicy;
import musinsa.pay.point_server.domain.policy.PointUserPolicy;
import musinsa.pay.point_server.domain.policy.PolicyStatus;
import musinsa.pay.point_server.domain.transaction.CreatedByType;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import musinsa.pay.point_server.persistence.account.PointAccountRepository;
import musinsa.pay.point_server.persistence.account.PointBalanceRepository;
import musinsa.pay.point_server.persistence.earn.PointEarnRepository;
import musinsa.pay.point_server.persistence.ledger.PointLedgerRepository;
import musinsa.pay.point_server.persistence.policy.PointPolicyRepository;
import musinsa.pay.point_server.persistence.policy.PointUserPolicyRepository;
import musinsa.pay.point_server.persistence.transaction.PointTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("포인트 적립 서비스")
class EarnPointServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final Long SECOND_ACCOUNT_ID = 2L;
    private static final Long SECOND_USER_ID = 2L;
    private static final Long POINT_POLICY_ID = 100L;
    private static final Long USER_POLICY_ID = 200L;
    private static final Long SECOND_USER_POLICY_ID = 201L;
    private static final Long TRANSACTION_ID = 10L;
    private static final Long EARN_ID = 20L;
    private static final Long LEDGER_ID = 30L;
    private static final String IDEMPOTENCY_KEY = "earn-request-001";
    private static final String POINT_KEY = "260511TST000000001";
    private static final LocalDateTime STATUS_UPDATED_AT = LocalDateTime.of(2026, 5, 11, 0, 0);

    @Mock
    private PointCommandTemplate pointCommandTemplate;

    @Mock
    private PointKeyGenerator pointKeyGenerator;

    @Mock
    private PointAccountRepository accountRepository;

    @Mock
    private PointBalanceRepository balanceRepository;

    @Mock
    private PointPolicyRepository pointPolicyRepository;

    @Mock
    private PointUserPolicyRepository pointUserPolicyRepository;

    @Mock
    private PointTransactionRepository transactionRepository;

    @Mock
    private PointEarnRepository earnRepository;

    @Mock
    private PointLedgerRepository ledgerRepository;

    private EarnPointService earnPointService;
    private PointBalance balance;
    private AtomicReference<PointTransaction> savedTransaction;
    private AtomicReference<PointEarn> savedEarn;
    private AtomicReference<PointLedger> savedLedger;

    @BeforeEach
    void setUp() {
        EarnPointContextFactory contextFactory = new EarnPointContextFactory(
            accountRepository,
            pointPolicyRepository,
            pointUserPolicyRepository
        );
        EarnPointProcessor processor = new EarnPointProcessor(
            contextFactory,
            new EarnPointValidator(),
            pointKeyGenerator,
            transactionRepository,
            earnRepository,
            balanceRepository,
            ledgerRepository
        );

        earnPointService = new EarnPointService(
            pointCommandTemplate,
            processor
        );

        balance = balance("1000");
        savedTransaction = new AtomicReference<>();
        savedEarn = new AtomicReference<>();
        savedLedger = new AtomicReference<>();
    }

    @Test
    @DisplayName("정상 적립이면 거래, 적립, 원장을 생성하고 잔액을 증가시킨다")
    void earnPoint() {
        // given
        EarnPointRequest request = request("500");
        givenIdempotencyExecutesOperation();
        givenPolicies("100000", "10000");
        givenAccount(AccountStatus.ACTIVE);
        givenEarnWillBeSaved("1500");

        // when
        EarnPointResponse response = earnPointService.earn(IDEMPOTENCY_KEY, request);

        // then
        assertThat(response.pointKey()).isEqualTo(POINT_KEY);
        assertThat(response.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(response.earnId()).isEqualTo(EARN_ID);
        assertThat(response.amount()).isEqualByComparingTo("500");
        assertThat(response.balanceBefore()).isEqualByComparingTo("1000");
        assertThat(response.balanceAfter()).isEqualByComparingTo("1500");

        assertThat(savedTransaction.get().getTransactionType()).isEqualTo(TransactionType.EARN);
        assertThat(savedTransaction.get().getPointKey()).isEqualTo(POINT_KEY);
        assertThat(savedEarn.get().getStatus()).isEqualTo(EarnStatus.AVAILABLE);
        assertThat(savedLedger.get().getLedgerType()).isEqualTo(LedgerType.EARN_INCREASE);
        verify(balanceRepository).updateBalanceAmount(ACCOUNT_ID, amount("1500"));
    }

    @Test
    @DisplayName("1회 최대 적립 금액을 초과하면 적립하지 않는다")
    void rejectExceededEarnAmount() {
        // given
        EarnPointRequest request = request("100001");
        givenIdempotencyExecutesOperation();
        givenPolicies("100000", "1000000");
        givenAccount(AccountStatus.ACTIVE);

        // when
        assertThatThrownBy(() -> earnPointService.earn(IDEMPOTENCY_KEY, request))
            // then
            .isInstanceOf(BaseException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.POINT_EARN_AMOUNT_EXCEEDED);

        verifyNoEarnSideEffects();
    }

    @Test
    @DisplayName("보유 가능 한도를 초과하면 적립하지 않는다")
    void rejectExceededBalanceLimit() {
        // given
        EarnPointRequest request = request("500");
        givenIdempotencyExecutesOperation();
        givenPolicies("100000", "1200");
        givenAccount(AccountStatus.ACTIVE);

        // when
        assertThatThrownBy(() -> earnPointService.earn(IDEMPOTENCY_KEY, request))
            // then
            .isInstanceOf(BaseException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.POINT_BALANCE_LIMIT_EXCEEDED);

        verifyNoEarnSideEffects();
    }

    @Test
    @DisplayName("사용자별 보유 한도 정책에 따라 같은 적립 요청도 결과가 달라진다")
    void applyDifferentBalanceLimitByUserPolicy() {
        // given
        givenIdempotencyExecutesOperation();
        givenPointPolicy("100000");

        givenAccount(ACCOUNT_ID, USER_ID, USER_POLICY_ID, AccountStatus.ACTIVE);
        givenUserPolicy(USER_POLICY_ID, "1200", PolicyStatus.ACTIVE);

        // when
        // then
        assertEarnRejected(request(ACCOUNT_ID, "500"), ErrorCode.POINT_BALANCE_LIMIT_EXCEEDED);

        // given
        balance = balance(SECOND_ACCOUNT_ID, "1000");
        givenAccount(SECOND_ACCOUNT_ID, SECOND_USER_ID, SECOND_USER_POLICY_ID, AccountStatus.ACTIVE);
        givenUserPolicy(SECOND_USER_POLICY_ID, "2000", PolicyStatus.ACTIVE);
        givenEarnWillBeSaved(SECOND_ACCOUNT_ID, "1500");

        // when
        EarnPointResponse response = earnPointService.earn(IDEMPOTENCY_KEY, request(SECOND_ACCOUNT_ID, "500"));

        // then
        assertThat(response.accountId()).isEqualTo(SECOND_ACCOUNT_ID);
        assertThat(response.balanceBefore()).isEqualByComparingTo("1000");
        assertThat(response.balanceAfter()).isEqualByComparingTo("1500");
        verify(balanceRepository).updateBalanceAmount(SECOND_ACCOUNT_ID, amount("1500"));
    }

    @Test
    @DisplayName("수기 지급 적립은 관리자 행위자로만 요청할 수 있다")
    void rejectManualEarnByNonAdmin() {
        // given
        EarnPointRequest request = manualRequestByUser();
        givenIdempotencyExecutesOperation();
        givenPolicies("100000", "10000");
        givenAccount(AccountStatus.ACTIVE);

        // when
        assertThatThrownBy(() -> earnPointService.earn(IDEMPOTENCY_KEY, request))
            // then
            .isInstanceOf(BaseException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.POINT_MANUAL_EARN_FORBIDDEN);

        verifyNoEarnSideEffects();
    }

    @Test
    @DisplayName("0원 적립 요청이면 적립하지 않는다")
    void rejectZeroEarnAmount() {
        // given
        EarnPointRequest request = request("0");
        givenIdempotencyExecutesOperation();
        givenPolicies("100000", "10000");
        givenAccount(AccountStatus.ACTIVE);

        // when
        // then
        assertEarnRejected(request, ErrorCode.POINT_AMOUNT_INVALID);
    }

    @Test
    @DisplayName("음수 적립 요청이면 적립하지 않는다")
    void rejectNegativeEarnAmount() {
        // given
        EarnPointRequest request = request("-1");
        givenIdempotencyExecutesOperation();
        givenPolicies("100000", "10000");
        givenAccount(AccountStatus.ACTIVE);

        // when
        // then
        assertEarnRejected(request, ErrorCode.POINT_AMOUNT_INVALID);
    }

    @Test
    @DisplayName("소수점 적립 요청이면 적립하지 않는다")
    void rejectDecimalEarnAmount() {
        // given
        EarnPointRequest request = request("10.5");
        givenIdempotencyExecutesOperation();
        givenPolicies("100000", "10000");
        givenAccount(AccountStatus.ACTIVE);

        // when
        // then
        assertEarnRejected(request, ErrorCode.POINT_AMOUNT_INVALID);
    }

    @Test
    @DisplayName("만료일이 최소값보다 작으면 적립하지 않는다")
    void rejectExpireDaysLessThanMinimum() {
        // given
        EarnPointRequest request = request("500", 0);
        givenIdempotencyExecutesOperation();
        givenPolicies("100000", "10000");
        givenAccount(AccountStatus.ACTIVE);

        // when
        // then
        assertEarnRejected(request, ErrorCode.POINT_EXPIRE_DAYS_INVALID);
    }

    @Test
    @DisplayName("만료일이 최대값보다 크면 적립하지 않는다")
    void rejectExpireDaysGreaterThanMaximum() {
        // given
        EarnPointRequest request = request("500", 1825);
        givenIdempotencyExecutesOperation();
        givenPolicies("100000", "10000");
        givenAccount(AccountStatus.ACTIVE);

        // when
        // then
        assertEarnRejected(request, ErrorCode.POINT_EXPIRE_DAYS_INVALID);
    }

    @Test
    @DisplayName("차단 계정이면 적립하지 않는다")
    void rejectBlockedAccount() {
        // given
        EarnPointRequest request = request("500");
        givenIdempotencyExecutesOperation();
        givenAccount(AccountStatus.BLOCKED);

        // when
        // then
        assertEarnRejected(request, ErrorCode.ACCOUNT_BLOCKED);
    }

    @Test
    @DisplayName("삭제 계정이면 적립하지 않는다")
    void rejectDeletedAccount() {
        // given
        EarnPointRequest request = request("500");
        givenIdempotencyExecutesOperation();
        givenAccount(AccountStatus.DELETED);

        // when
        // then
        assertEarnRejected(request, ErrorCode.ACCOUNT_DELETED);
    }

    @Test
    @DisplayName("비활성 사용자 정책이면 적립하지 않는다")
    void rejectInactiveUserPolicy() {
        // given
        EarnPointRequest request = request("500");
        givenIdempotencyExecutesOperation();
        givenPointPolicy("100000");
        givenAccount(AccountStatus.ACTIVE);
        givenUserPolicy("10000", PolicyStatus.INACTIVE);

        // when
        // then
        assertEarnRejected(request, ErrorCode.POINT_POLICY_INACTIVE);
    }

    @Test
    @DisplayName("사용취소 재적립 유형을 직접 요청하면 적립하지 않는다")
    void rejectUseCancelRestoreEarnType() {
        // given
        EarnPointRequest request = request("500", null, EarnType.USE_CANCEL_RESTORE);
        givenIdempotencyExecutesOperation();
        givenPolicies("100000", "10000");
        givenAccount(AccountStatus.ACTIVE);

        // when
        // then
        assertEarnRejected(request, ErrorCode.POINT_EARN_TYPE_INVALID);
    }

    @Test
    @DisplayName("필수 요청값이 없으면 멱등성 처리 전에 거절한다")
    void rejectInvalidRequestBeforeIdempotency() {
        // given
        EarnPointRequest request = new EarnPointRequest(
            ACCOUNT_ID,
            null,
            EarnType.NORMAL,
            null,
            "order reward"
        );

        // when
        assertEarnRejected(request, ErrorCode.INVALID_REQUEST);

        // then
        verify(pointCommandTemplate, never()).execute(any(), any());
    }

    private void givenIdempotencyExecutesOperation() {
        when(pointCommandTemplate.execute(any(), any()))
            .thenAnswer(invocation -> invocation
                .<IdempotentOperation>getArgument(1)
                .execute(balance));
    }

    private void givenPolicies(
        String maxEarnAmount,
        String maxBalanceAmount
    ) {
        givenPointPolicy(maxEarnAmount);
        givenUserPolicy(maxBalanceAmount, PolicyStatus.ACTIVE);
    }

    private void givenPointPolicy(String maxEarnAmount) {
        PointPolicy pointPolicy = persisted(pointPolicy(maxEarnAmount), POINT_POLICY_ID);
        when(pointPolicyRepository.findByPolicyCodeAndStatus(eq("DEFAULT"), eq(PolicyStatus.ACTIVE)))
            .thenReturn(Optional.of(pointPolicy));
    }

    private void givenUserPolicy(
        String maxBalanceAmount,
        PolicyStatus status
    ) {
        givenUserPolicy(USER_POLICY_ID, maxBalanceAmount, status);
    }

    private void givenUserPolicy(
        Long userPolicyId,
        String maxBalanceAmount,
        PolicyStatus status
    ) {
        PointUserPolicy userPolicy = persisted(userPolicy(maxBalanceAmount, status), userPolicyId);
        when(pointUserPolicyRepository.findById(userPolicyId)).thenReturn(Optional.of(userPolicy));
    }

    private PointPolicy pointPolicy(String maxEarnAmount) {
        return PointPolicy.builder()
            .policyCode("DEFAULT")
            .name("기본 포인트 정책")
            .maxEarnAmount(amount(maxEarnAmount))
            .defaultExpireDays(365)
            .minExpireDays(1)
            .maxExpireDays(1824)
            .status(PolicyStatus.ACTIVE)
            .statusUpdatedAt(STATUS_UPDATED_AT)
            .statusUpdatedBy("SYSTEM")
            .build();
    }

    private PointUserPolicy userPolicy(
        String maxBalanceAmount,
        PolicyStatus status
    ) {
        return PointUserPolicy.builder()
            .policyCode("DEFAULT")
            .name("기본 사용자 포인트 정책")
            .maxBalanceAmount(amount(maxBalanceAmount))
            .status(status)
            .statusUpdatedAt(STATUS_UPDATED_AT)
            .statusUpdatedBy("SYSTEM")
            .build();
    }

    private void givenAccount(AccountStatus status) {
        givenAccount(ACCOUNT_ID, USER_ID, USER_POLICY_ID, status);
    }

    private void givenAccount(
        Long accountId,
        Long userId,
        Long userPolicyId,
        AccountStatus status
    ) {
        PointAccount account = persisted(pointAccount(userId, userPolicyId, status), accountId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    }

    private PointAccount pointAccount(
        Long userId,
        Long userPolicyId,
        AccountStatus status
    ) {
        return PointAccount.builder()
            .userId(userId)
            .pointUserPolicyId(userPolicyId)
            .status(status)
            .statusUpdatedAt(STATUS_UPDATED_AT)
            .statusUpdatedBy("SYSTEM")
            .build();
    }

    private void givenEarnWillBeSaved(String balanceAfter) {
        givenEarnWillBeSaved(ACCOUNT_ID, balanceAfter);
    }

    private void givenEarnWillBeSaved(
        Long accountId,
        String balanceAfter
    ) {
        givenPointKey();
        givenTransactionSaved();
        givenEarnSaved();
        givenLedgerSaved();
        givenBalanceUpdated(accountId, balanceAfter);
        givenResultQueries();
    }

    private void givenPointKey() {
        when(pointKeyGenerator.generate()).thenReturn(POINT_KEY);
    }

    private void givenTransactionSaved() {
        when(transactionRepository.save(any(PointTransaction.class))).thenAnswer(invocation -> {
            PointTransaction transaction = persisted(invocation.getArgument(0), TRANSACTION_ID);
            savedTransaction.set(transaction);
            return transaction;
        });
    }

    private void givenEarnSaved() {
        when(earnRepository.save(any(PointEarn.class))).thenAnswer(invocation -> {
            PointEarn earn = persisted(invocation.getArgument(0), EARN_ID);
            savedEarn.set(earn);
            return earn;
        });
    }

    private void givenLedgerSaved() {
        when(ledgerRepository.save(any(PointLedger.class))).thenAnswer(invocation -> {
            PointLedger ledger = persisted(invocation.getArgument(0), LEDGER_ID);
            savedLedger.set(ledger);
            return ledger;
        });
    }

    private void givenBalanceUpdated(String balanceAfter) {
        givenBalanceUpdated(ACCOUNT_ID, balanceAfter);
    }

    private void givenBalanceUpdated(
        Long accountId,
        String balanceAfter
    ) {
        when(balanceRepository.updateBalanceAmount(accountId, amount(balanceAfter))).thenReturn(1);
    }

    private void givenResultQueries() {
        when(transactionRepository.findById(TRANSACTION_ID))
            .thenAnswer(invocation -> Optional.of(savedTransaction.get()));
        when(earnRepository.findByTransactionId(TRANSACTION_ID))
            .thenAnswer(invocation -> Optional.of(savedEarn.get()));
        when(ledgerRepository.findFirstByTransactionIdAndLedgerType(TRANSACTION_ID, LedgerType.EARN_INCREASE))
            .thenAnswer(invocation -> Optional.of(savedLedger.get()));
    }

    private void verifyNoEarnSideEffects() {
        verify(transactionRepository, never()).save(any());
        verify(earnRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
        verify(balanceRepository, never()).updateBalanceAmount(any(), any());
    }

    private void assertEarnRejected(
        EarnPointRequest request,
        ErrorCode errorCode
    ) {
        assertThatThrownBy(() -> earnPointService.earn(IDEMPOTENCY_KEY, request))
            .isInstanceOf(BaseException.class)
            .extracting("errorCode")
            .isEqualTo(errorCode);

        verifyNoEarnSideEffects();
    }

    private EarnPointRequest request(String amount) {
        return request(ACCOUNT_ID, amount);
    }

    private EarnPointRequest request(
        Long accountId,
        String amount
    ) {
        return request(accountId, amount, null);
    }

    private EarnPointRequest request(
        String amount,
        Integer expireDays
    ) {
        return request(ACCOUNT_ID, amount, expireDays, EarnType.NORMAL);
    }

    private EarnPointRequest request(
        Long accountId,
        String amount,
        Integer expireDays
    ) {
        return request(accountId, amount, expireDays, EarnType.NORMAL);
    }

    private EarnPointRequest request(
        String amount,
        Integer expireDays,
        EarnType earnType
    ) {
        return request(ACCOUNT_ID, amount, expireDays, earnType);
    }

    private EarnPointRequest request(
        Long accountId,
        String amount,
        Integer expireDays,
        EarnType earnType
    ) {
        return new EarnPointRequest(
            accountId,
            amount(amount),
            earnType,
            expireDays,
            "order reward"
        );
    }

    private EarnPointRequest manualRequestByUser() {
        return new EarnPointRequest(
            ACCOUNT_ID,
            amount("500"),
            EarnType.MANUAL,
            null,
            "manual earn"
        );
    }

    private PointBalance balance(String balanceAmount) {
        return balance(ACCOUNT_ID, balanceAmount);
    }

    private PointBalance balance(
        Long accountId,
        String balanceAmount
    ) {
        return PointBalance.builder()
            .accountId(accountId)
            .balanceAmount(amount(balanceAmount))
            .build();
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
