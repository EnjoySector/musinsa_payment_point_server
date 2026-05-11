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
import musinsa.pay.point_server.application.earn.dto.EarnCancelRequest;
import musinsa.pay.point_server.application.earn.dto.EarnCancelResponse;
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
import musinsa.pay.point_server.domain.earn.PointEarnCancel;
import musinsa.pay.point_server.domain.ledger.LedgerType;
import musinsa.pay.point_server.domain.ledger.PointLedger;
import musinsa.pay.point_server.domain.transaction.CreatedByType;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import musinsa.pay.point_server.persistence.account.PointAccountRepository;
import musinsa.pay.point_server.persistence.account.PointBalanceRepository;
import musinsa.pay.point_server.persistence.earn.PointEarnCancelRepository;
import musinsa.pay.point_server.persistence.earn.PointEarnRepository;
import musinsa.pay.point_server.persistence.ledger.PointLedgerRepository;
import musinsa.pay.point_server.persistence.transaction.PointTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("포인트 적립취소 서비스")
class EarnCancelServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final Long USER_POLICY_ID = 200L;
    private static final Long POINT_POLICY_ID = 100L;
    private static final Long EARN_TRANSACTION_ID = 10L;
    private static final Long CANCEL_TRANSACTION_ID = 11L;
    private static final Long EARN_ID = 20L;
    private static final Long EARN_CANCEL_ID = 21L;
    private static final Long LEDGER_ID = 30L;
    private static final String IDEMPOTENCY_KEY = "earn-cancel-request-001";
    private static final String EARN_POINT_KEY = "260511ERN000000001";
    private static final String CANCEL_POINT_KEY = "260511CXL000000001";
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
    private PointTransactionRepository transactionRepository;

    @Mock
    private PointEarnRepository earnRepository;

    @Mock
    private PointEarnCancelRepository earnCancelRepository;

    @Mock
    private PointLedgerRepository ledgerRepository;

    private EarnCancelService earnCancelService;
    private PointBalance balance;
    private PointTransaction earnTransaction;
    private AtomicReference<PointTransaction> savedTransaction;
    private AtomicReference<PointEarnCancel> savedEarnCancel;
    private AtomicReference<PointLedger> savedLedger;

    @BeforeEach
    void setUp() {
        EarnCancelContextFactory contextFactory = new EarnCancelContextFactory(
            accountRepository,
            transactionRepository,
            earnRepository
        );
        EarnCancelProcessor processor = new EarnCancelProcessor(
            contextFactory,
            new EarnCancelValidator(),
            pointKeyGenerator,
            transactionRepository,
            earnCancelRepository,
            earnRepository,
            balanceRepository,
            ledgerRepository
        );

        earnCancelService = new EarnCancelService(
            pointCommandTemplate,
            processor
        );

        balance = balance("1000");
        earnTransaction = earnTransaction();
        savedTransaction = new AtomicReference<>();
        savedEarnCancel = new AtomicReference<>();
        savedLedger = new AtomicReference<>();
    }

    @Test
    @DisplayName("미사용 적립이면 전체 금액을 취소하고 잔액을 감소시킨다")
    void cancelEarn() {
        // given
        EarnCancelRequest request = request(EARN_POINT_KEY);
        givenIdempotencyExecutesOperation();
        givenAccount(AccountStatus.ACTIVE);
        givenEarnTarget(availableEarn());
        givenCancelWillBeSaved("500");

        // when
        EarnCancelResponse response = earnCancelService.cancel(IDEMPOTENCY_KEY, request);

        // then
        assertThat(response.pointKey()).isEqualTo(CANCEL_POINT_KEY);
        assertThat(response.transactionId()).isEqualTo(CANCEL_TRANSACTION_ID);
        assertThat(response.earnCancelId()).isEqualTo(EARN_CANCEL_ID);
        assertThat(response.earnPointKey()).isEqualTo(EARN_POINT_KEY);
        assertThat(response.cancelAmount()).isEqualByComparingTo("500");
        assertThat(response.balanceBefore()).isEqualByComparingTo("1000");
        assertThat(response.balanceAfter()).isEqualByComparingTo("500");

        assertThat(savedTransaction.get().getTransactionType()).isEqualTo(TransactionType.EARN_CANCEL);
        assertThat(savedTransaction.get().getRelatedTransactionId()).isEqualTo(EARN_TRANSACTION_ID);
        assertThat(savedEarnCancel.get().getEarnId()).isEqualTo(EARN_ID);
        assertThat(savedLedger.get().getLedgerType()).isEqualTo(LedgerType.EARN_CANCEL_DECREASE);
        assertThat(savedLedger.get().getDeltaAmount()).isEqualByComparingTo("-500");
        verify(earnRepository).cancelEarn(
            EARN_ID,
            ACCOUNT_ID,
            BigDecimal.ZERO,
            amount("500"),
            EarnStatus.CANCELED
        );
        verify(balanceRepository).updateBalanceAmount(ACCOUNT_ID, amount("500"));
    }

    @Test
    @DisplayName("이미 일부 사용된 적립이면 취소하지 않는다")
    void rejectConsumedEarn() {
        // given
        EarnCancelRequest request = request(EARN_POINT_KEY);
        givenIdempotencyExecutesOperation();
        givenAccount(AccountStatus.ACTIVE);
        givenEarnTarget(consumedEarn());

        // when
        // then
        assertCancelRejected(request, ErrorCode.POINT_EARN_ALREADY_USED);
    }

    @Test
    @DisplayName("이미 취소된 적립이면 다시 취소하지 않는다")
    void rejectAlreadyCanceledEarn() {
        // given
        EarnCancelRequest request = request(EARN_POINT_KEY);
        givenIdempotencyExecutesOperation();
        givenAccount(AccountStatus.ACTIVE);
        givenEarnTarget(canceledEarn());

        // when
        // then
        assertCancelRejected(request, ErrorCode.POINT_EARN_ALREADY_CANCELED);
    }

    @Test
    @DisplayName("이미 만료된 적립이면 취소하지 않는다")
    void rejectExpiredEarn() {
        // given
        EarnCancelRequest request = request(EARN_POINT_KEY);
        givenIdempotencyExecutesOperation();
        givenAccount(AccountStatus.ACTIVE);
        givenEarnTarget(expiredEarn());

        // when
        // then
        assertCancelRejected(request, ErrorCode.POINT_EARN_EXPIRED);
    }

    @Test
    @DisplayName("적립취소 금액보다 계정 잔액이 적으면 취소하지 않는다")
    void rejectInsufficientBalanceForEarnCancel() {
        // given
        balance = balance("100");
        EarnCancelRequest request = request(EARN_POINT_KEY);
        givenIdempotencyExecutesOperation();
        givenAccount(AccountStatus.ACTIVE);
        givenEarnTarget(availableEarn());

        // when
        // then
        assertCancelRejected(request, ErrorCode.POINT_EARN_CANCEL_BALANCE_INSUFFICIENT);
    }

    @Test
    @DisplayName("같은 멱등성 키 재호출이면 기존 적립취소 거래 결과를 반환한다")
    void returnExistingCancelResultByIdempotency() {
        // given
        EarnCancelRequest request = request(EARN_POINT_KEY);
        givenIdempotencyReturnsExistingTransactionId();
        givenExistingCancelResult();

        // when
        EarnCancelResponse response = earnCancelService.cancel(IDEMPOTENCY_KEY, request);

        // then
        assertThat(response.transactionId()).isEqualTo(CANCEL_TRANSACTION_ID);
        assertThat(response.earnCancelId()).isEqualTo(EARN_CANCEL_ID);
        assertThat(response.balanceBefore()).isEqualByComparingTo("1000");
        assertThat(response.balanceAfter()).isEqualByComparingTo("500");
        verifyNoCancelSideEffects();
    }

    @Test
    @DisplayName("필수 요청값이 없으면 멱등성 처리 전에 거절한다")
    void rejectInvalidRequestBeforeIdempotency() {
        // given
        EarnCancelRequest request = new EarnCancelRequest(
            ACCOUNT_ID,
            " ",
            "wrong earn"
        );

        // when
        assertCancelRejected(request, ErrorCode.INVALID_REQUEST);

        // then
        verify(pointCommandTemplate, never()).execute(any(), any());
    }

    private void givenIdempotencyExecutesOperation() {
        when(pointCommandTemplate.execute(any(), any()))
            .thenAnswer(invocation -> invocation
                .<IdempotentOperation>getArgument(1)
                .execute(balance));
    }

    private void givenIdempotencyReturnsExistingTransactionId() {
        when(pointCommandTemplate.execute(any(), any()))
            .thenReturn(CANCEL_TRANSACTION_ID);
    }

    private void givenAccount(AccountStatus status) {
        PointAccount account = persisted(pointAccount(status), ACCOUNT_ID);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    }

    private PointAccount pointAccount(AccountStatus status) {
        return PointAccount.builder()
            .userId(USER_ID)
            .pointUserPolicyId(USER_POLICY_ID)
            .status(status)
            .statusUpdatedAt(STATUS_UPDATED_AT)
            .statusUpdatedBy("SYSTEM")
            .build();
    }

    private void givenEarnTarget(PointEarn earn) {
        when(transactionRepository.findByPointKeyAndTransactionType(EARN_POINT_KEY, TransactionType.EARN))
            .thenReturn(Optional.of(earnTransaction));
        when(earnRepository.findByTransactionIdForUpdate(EARN_TRANSACTION_ID))
            .thenReturn(Optional.of(earn));
    }

    private void givenCancelWillBeSaved(String balanceAfter) {
        givenPointKey();
        givenCancelTransactionSaved();
        givenEarnCancelSaved();
        givenEarnUpdated();
        givenBalanceUpdated(balanceAfter);
        givenLedgerSaved();
        givenExistingCancelResult();
    }

    private void givenPointKey() {
        when(pointKeyGenerator.generate()).thenReturn(CANCEL_POINT_KEY);
    }

    private void givenCancelTransactionSaved() {
        when(transactionRepository.save(any(PointTransaction.class))).thenAnswer(invocation -> {
            PointTransaction transaction = persisted(invocation.getArgument(0), CANCEL_TRANSACTION_ID);
            savedTransaction.set(transaction);
            return transaction;
        });
    }

    private void givenEarnCancelSaved() {
        when(earnCancelRepository.save(any(PointEarnCancel.class))).thenAnswer(invocation -> {
            PointEarnCancel earnCancel = persisted(invocation.getArgument(0), EARN_CANCEL_ID);
            savedEarnCancel.set(earnCancel);
            return earnCancel;
        });
    }

    private void givenEarnUpdated() {
        when(earnRepository.cancelEarn(EARN_ID, ACCOUNT_ID, BigDecimal.ZERO, amount("500"), EarnStatus.CANCELED))
            .thenReturn(1);
    }

    private void givenBalanceUpdated(String balanceAfter) {
        when(balanceRepository.updateBalanceAmount(ACCOUNT_ID, amount(balanceAfter))).thenReturn(1);
    }

    private void givenLedgerSaved() {
        when(ledgerRepository.save(any(PointLedger.class))).thenAnswer(invocation -> {
            PointLedger ledger = persisted(invocation.getArgument(0), LEDGER_ID);
            savedLedger.set(ledger);
            return ledger;
        });
    }

    private void givenExistingCancelResult() {
        PointTransaction cancelTransaction = savedTransaction.get() == null
            ? cancelTransaction()
            : savedTransaction.get();
        PointEarnCancel earnCancel = savedEarnCancel.get() == null
            ? earnCancel()
            : savedEarnCancel.get();
        PointLedger ledger = savedLedger.get() == null
            ? ledger()
            : savedLedger.get();

        when(transactionRepository.findById(CANCEL_TRANSACTION_ID)).thenReturn(Optional.of(cancelTransaction));
        when(transactionRepository.findById(EARN_TRANSACTION_ID)).thenReturn(Optional.of(earnTransaction));
        when(earnCancelRepository.findByTransactionId(CANCEL_TRANSACTION_ID)).thenReturn(Optional.of(earnCancel));
        when(ledgerRepository.findFirstByTransactionIdAndLedgerType(CANCEL_TRANSACTION_ID, LedgerType.EARN_CANCEL_DECREASE))
            .thenReturn(Optional.of(ledger));
    }

    private void verifyNoCancelSideEffects() {
        verify(transactionRepository, never()).save(any());
        verify(earnCancelRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
        verify(earnRepository, never()).cancelEarn(any(), any(), any(), any(), any());
        verify(balanceRepository, never()).updateBalanceAmount(any(), any());
    }

    private void assertCancelRejected(
        EarnCancelRequest request,
        ErrorCode errorCode
    ) {
        assertThatThrownBy(() -> earnCancelService.cancel(IDEMPOTENCY_KEY, request))
            .isInstanceOf(BaseException.class)
            .extracting("errorCode")
            .isEqualTo(errorCode);

        verifyNoCancelSideEffects();
    }

    private EarnCancelRequest request(String earnPointKey) {
        return new EarnCancelRequest(
            ACCOUNT_ID,
            earnPointKey,
            "earn cancel"
        );
    }

    private PointTransaction earnTransaction() {
        return persisted(PointTransaction.builder()
            .pointKey(EARN_POINT_KEY)
            .accountId(ACCOUNT_ID)
            .pointPolicyId(POINT_POLICY_ID)
            .pointUserPolicyId(USER_POLICY_ID)
            .transactionType(TransactionType.EARN)
            .amount(amount("500"))
            .createdByType(CreatedByType.USER)
            .createdById(String.valueOf(USER_ID))
            .reason("order reward")
            .build(), EARN_TRANSACTION_ID);
    }

    private PointTransaction cancelTransaction() {
        return persisted(PointTransaction.builder()
            .pointKey(CANCEL_POINT_KEY)
            .accountId(ACCOUNT_ID)
            .pointPolicyId(POINT_POLICY_ID)
            .pointUserPolicyId(USER_POLICY_ID)
            .transactionType(TransactionType.EARN_CANCEL)
            .amount(amount("500"))
            .relatedTransactionId(EARN_TRANSACTION_ID)
            .createdByType(CreatedByType.ADMIN)
            .createdById("admin-1")
            .reason("earn cancel")
            .build(), CANCEL_TRANSACTION_ID);
    }

    private PointEarn availableEarn() {
        return earn(
            amount("500"),
            amount("500"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            EarnStatus.AVAILABLE,
            LocalDateTime.now().plusDays(10)
        );
    }

    private PointEarn consumedEarn() {
        return earn(
            amount("500"),
            amount("300"),
            amount("200"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            EarnStatus.AVAILABLE,
            LocalDateTime.now().plusDays(10)
        );
    }

    private PointEarn canceledEarn() {
        return earn(
            amount("500"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            amount("500"),
            BigDecimal.ZERO,
            EarnStatus.CANCELED,
            LocalDateTime.now().plusDays(10)
        );
    }

    private PointEarn expiredEarn() {
        return earn(
            amount("500"),
            amount("500"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            EarnStatus.AVAILABLE,
            LocalDateTime.now().minusDays(1)
        );
    }

    private PointEarn earn(
        BigDecimal earnAmount,
        BigDecimal availableAmount,
        BigDecimal consumedAmount,
        BigDecimal cancelledAmount,
        BigDecimal expiredAmount,
        EarnStatus status,
        LocalDateTime expiresAt
    ) {
        return persisted(PointEarn.builder()
            .transactionId(EARN_TRANSACTION_ID)
            .accountId(ACCOUNT_ID)
            .earnType(EarnType.NORMAL)
            .earnAmount(earnAmount)
            .availableAmount(availableAmount)
            .consumedAmount(consumedAmount)
            .cancelledAmount(cancelledAmount)
            .expiredAmount(expiredAmount)
            .expiresAt(expiresAt)
            .status(status)
            .build(), EARN_ID);
    }

    private PointEarnCancel earnCancel() {
        return persisted(PointEarnCancel.builder()
            .transactionId(CANCEL_TRANSACTION_ID)
            .accountId(ACCOUNT_ID)
            .earnId(EARN_ID)
            .cancelAmount(amount("500"))
            .build(), EARN_CANCEL_ID);
    }

    private PointLedger ledger() {
        return persisted(PointLedger.builder()
            .transactionId(CANCEL_TRANSACTION_ID)
            .accountId(ACCOUNT_ID)
            .earnId(EARN_ID)
            .ledgerType(LedgerType.EARN_CANCEL_DECREASE)
            .deltaAmount(amount("-500"))
            .accountBalanceBefore(amount("1000"))
            .accountBalanceAfter(amount("500"))
            .earnAvailableBefore(amount("500"))
            .earnAvailableAfter(BigDecimal.ZERO)
            .description("포인트 적립취소")
            .build(), LEDGER_ID);
    }

    private PointBalance balance(String balanceAmount) {
        return PointBalance.builder()
            .accountId(ACCOUNT_ID)
            .balanceAmount(amount(balanceAmount))
            .build();
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
