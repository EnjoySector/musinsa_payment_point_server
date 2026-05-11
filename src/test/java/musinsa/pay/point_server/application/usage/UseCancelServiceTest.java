package musinsa.pay.point_server.application.usage;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import musinsa.pay.point_server.application.idempotency.IdempotentOperation;
import musinsa.pay.point_server.application.point.PointCommandTemplate;
import musinsa.pay.point_server.application.usage.dto.UseCancelRequest;
import musinsa.pay.point_server.application.usage.dto.UseCancelResponse;
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
import musinsa.pay.point_server.domain.policy.PolicyStatus;
import musinsa.pay.point_server.domain.transaction.CreatedByType;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import musinsa.pay.point_server.domain.usage.PointUsage;
import musinsa.pay.point_server.domain.usage.PointUsageAllocation;
import musinsa.pay.point_server.domain.usage.PointUsageCancel;
import musinsa.pay.point_server.domain.usage.PointUsageCancelAllocation;
import musinsa.pay.point_server.domain.usage.RestoreType;
import musinsa.pay.point_server.domain.usage.UsageAllocationStatus;
import musinsa.pay.point_server.domain.usage.UsageStatus;
import musinsa.pay.point_server.persistence.account.PointAccountRepository;
import musinsa.pay.point_server.persistence.account.PointBalanceRepository;
import musinsa.pay.point_server.persistence.earn.PointEarnRepository;
import musinsa.pay.point_server.persistence.ledger.PointLedgerRepository;
import musinsa.pay.point_server.persistence.policy.PointPolicyRepository;
import musinsa.pay.point_server.persistence.transaction.PointTransactionRepository;
import musinsa.pay.point_server.persistence.usage.PointUsageAllocationRepository;
import musinsa.pay.point_server.persistence.usage.PointUsageCancelAllocationRepository;
import musinsa.pay.point_server.persistence.usage.PointUsageCancelRepository;
import musinsa.pay.point_server.persistence.usage.PointUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("포인트 사용취소 서비스")
class UseCancelServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final Long POINT_POLICY_ID = 100L;
    private static final Long USER_POLICY_ID = 200L;
    private static final Long USE_TRANSACTION_ID = 10L;
    private static final Long USE_CANCEL_TRANSACTION_ID = 11L;
    private static final Long RE_EARN_TRANSACTION_ID = 12L;
    private static final Long USAGE_ID = 20L;
    private static final Long USAGE_CANCEL_ID = 21L;
    private static final Long EXPIRED_EARN_ID = 101L;
    private static final Long ACTIVE_EARN_ID = 102L;
    private static final Long NEW_EARN_ID = 103L;
    private static final Long EXPIRED_ALLOCATION_ID = 201L;
    private static final Long ACTIVE_ALLOCATION_ID = 202L;
    private static final String IDEMPOTENCY_KEY = "use-cancel-request-001";
    private static final String USE_POINT_KEY = "260511USE000000001";
    private static final String USE_CANCEL_POINT_KEY = "260511UCL000000001";
    private static final String RE_EARN_POINT_KEY = "260511ERN000000002";
    private static final String ORDER_NO = "A1234";
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
    private PointUsageRepository usageRepository;

    @Mock
    private PointUsageAllocationRepository allocationRepository;

    @Mock
    private PointUsageCancelRepository usageCancelRepository;

    @Mock
    private PointUsageCancelAllocationRepository cancelAllocationRepository;

    @Mock
    private PointEarnRepository earnRepository;

    @Mock
    private PointLedgerRepository ledgerRepository;

    @Mock
    private PointPolicyRepository pointPolicyRepository;

    private UseCancelService useCancelService;
    private PointBalance balance;
    private PointTransaction useTransaction;
    private PointUsage usage;
    private AtomicReference<PointTransaction> savedUseCancelTransaction;
    private AtomicReference<PointTransaction> savedReEarnTransaction;
    private AtomicReference<PointUsageCancel> savedUsageCancel;
    private AtomicReference<PointEarn> savedReEarn;
    private List<PointUsageCancelAllocation> savedCancelAllocations;
    private List<PointLedger> savedLedgers;

    @BeforeEach
    void setUp() {
        UseCancelProcessor processor = new UseCancelProcessor(
            new UseCancelContextFactory(accountRepository, transactionRepository, usageRepository, pointPolicyRepository),
            new UseCancelValidator(),
            new PointUsageCancelAllocationCalculator(allocationRepository, earnRepository),
            pointKeyGenerator,
            transactionRepository,
            usageCancelRepository,
            cancelAllocationRepository,
            usageRepository,
            allocationRepository,
            earnRepository,
            balanceRepository,
            ledgerRepository
        );

        useCancelService = new UseCancelService(
            pointCommandTemplate,
            processor
        );

        balance = balance("300");
        useTransaction = useTransaction();
        usage = usage(UsageStatus.USED, "1200", "0");
        savedUseCancelTransaction = new AtomicReference<>();
        savedReEarnTransaction = new AtomicReference<>();
        savedUsageCancel = new AtomicReference<>();
        savedReEarn = new AtomicReference<>();
        savedCancelAllocations = new ArrayList<>();
        savedLedgers = new ArrayList<>();
    }

    @Test
    @DisplayName("사용취소 시 만료된 적립은 신규 적립으로, 유효 적립은 원 적립으로 복원한다")
    void cancelUseWithExpiredAndActiveEarns() {
        // given
        UseCancelRequest request = request("1100");
        givenIdempotencyExecutesOperation();
        givenBaseData();
        givenCancelableAllocations();
        givenUseCancelWillBeSaved("1400");

        // when
        UseCancelResponse response = useCancelService.cancel(IDEMPOTENCY_KEY, request);

        // then
        assertThat(response.pointKey()).isEqualTo(USE_CANCEL_POINT_KEY);
        assertThat(response.transactionId()).isEqualTo(USE_CANCEL_TRANSACTION_ID);
        assertThat(response.usageCancelId()).isEqualTo(USAGE_CANCEL_ID);
        assertThat(response.usePointKey()).isEqualTo(USE_POINT_KEY);
        assertThat(response.cancelAmount()).isEqualByComparingTo("1100");
        assertThat(response.balanceBefore()).isEqualByComparingTo("300");
        assertThat(response.balanceAfter()).isEqualByComparingTo("1400");

        assertThat(response.allocations()).extracting("originalEarnId")
            .containsExactly(EXPIRED_EARN_ID, ACTIVE_EARN_ID);
        assertThat(response.allocations()).extracting("restoredEarnId")
            .containsExactly(NEW_EARN_ID, ACTIVE_EARN_ID);
        assertThat(response.allocations()).extracting("cancelAmount")
            .containsExactly(amount("1000"), amount("100"));
        assertThat(response.allocations()).extracting("restoreType")
            .containsExactly(RestoreType.NEW_EARN, RestoreType.ORIGINAL_EARN);

        verify(earnRepository, never()).restoreEarn(eq(EXPIRED_EARN_ID), any(), any(), any(), any());
        verify(earnRepository).restoreEarn(ACTIVE_EARN_ID, ACCOUNT_ID, amount("400"), amount("100"), EarnStatus.AVAILABLE);
        verify(allocationRepository).updateCancelState(EXPIRED_ALLOCATION_ID, amount("1000"), UsageAllocationStatus.CANCELED);
        verify(allocationRepository).updateCancelState(ACTIVE_ALLOCATION_ID, amount("100"), UsageAllocationStatus.PARTIAL_CANCELED);
        verify(usageRepository).updateCancelState(USAGE_ID, ACCOUNT_ID, amount("1100"), UsageStatus.PARTIAL_CANCELED);
        verify(balanceRepository).updateBalanceAmount(ACCOUNT_ID, amount("1400"));
        assertThat(savedReEarnTransaction.get().getRelatedTransactionId()).isEqualTo(USE_CANCEL_TRANSACTION_ID);
        assertThat(savedReEarn.get().getOriginalEarnId()).isEqualTo(EXPIRED_EARN_ID);
        assertThat(savedReEarn.get().getSourceUsageCancelId()).isEqualTo(USAGE_CANCEL_ID);
        assertThat(savedLedgers).extracting("transactionId")
            .containsExactly(RE_EARN_TRANSACTION_ID, USE_CANCEL_TRANSACTION_ID);
    }

    @Test
    @DisplayName("취소 가능 금액을 초과하면 사용취소하지 않는다")
    void rejectOverCancelableAmount() {
        // given
        usage = usage(UsageStatus.PARTIAL_CANCELED, "1200", "1100");
        UseCancelRequest request = request("200");
        givenIdempotencyExecutesOperation();
        givenBaseData();

        // when
        // then
        assertCancelRejected(request, ErrorCode.POINT_USAGE_CANCEL_AMOUNT_EXCEEDED);
    }

    @Test
    @DisplayName("이미 전체 취소된 사용이면 다시 사용취소하지 않는다")
    void rejectAlreadyCanceledUsage() {
        // given
        usage = usage(UsageStatus.CANCELED, "1200", "1200");
        UseCancelRequest request = request("100");
        givenIdempotencyExecutesOperation();
        givenBaseData();

        // when
        // then
        assertCancelRejected(request, ErrorCode.POINT_USAGE_ALREADY_CANCELED);
    }

    @Test
    @DisplayName("취소 가능한 사용 배분이 부족하면 사용취소하지 않는다")
    void rejectInsufficientCancelableAllocation() {
        // given
        UseCancelRequest request = request("100");
        givenIdempotencyExecutesOperation();
        givenBaseData();
        when(allocationRepository.findCancelableAllocationsForUpdate(USAGE_ID)).thenReturn(List.of());

        // when
        // then
        assertCancelRejected(request, ErrorCode.POINT_USAGE_CANCEL_ALLOCATION_INSUFFICIENT);
    }

    @Test
    @DisplayName("0원 사용취소 요청이면 사용취소하지 않는다")
    void rejectZeroCancelAmount() {
        // given
        UseCancelRequest request = request("0");
        givenIdempotencyExecutesOperation();
        givenBaseData();

        // when
        // then
        assertCancelRejected(request, ErrorCode.POINT_AMOUNT_INVALID);
    }

    @Test
    @DisplayName("소수점 사용취소 요청이면 사용취소하지 않는다")
    void rejectDecimalCancelAmount() {
        // given
        UseCancelRequest request = request("10.5");
        givenIdempotencyExecutesOperation();
        givenAccount(AccountStatus.ACTIVE);
        givenUseTransaction();
        givenUsage();

        // when
        // then
        assertCancelRejected(request, ErrorCode.POINT_AMOUNT_INVALID);
    }

    @Test
    @DisplayName("같은 멱등성 키 재호출이면 기존 사용취소 거래 결과를 반환한다")
    void returnExistingCancelResultByIdempotency() {
        // given
        UseCancelRequest request = request("1100");
        givenIdempotencyReturnsExistingTransactionId();
        givenExistingUseCancelResult();

        // when
        UseCancelResponse response = useCancelService.cancel(IDEMPOTENCY_KEY, request);

        // then
        assertThat(response.transactionId()).isEqualTo(USE_CANCEL_TRANSACTION_ID);
        assertThat(response.usageCancelId()).isEqualTo(USAGE_CANCEL_ID);
        assertThat(response.balanceBefore()).isEqualByComparingTo("300");
        assertThat(response.balanceAfter()).isEqualByComparingTo("1400");
        verifyNoUseCancelSideEffects();
    }

    @Test
    @DisplayName("필수 요청값이 없으면 멱등성 처리 전에 거절한다")
    void rejectInvalidRequestBeforeIdempotency() {
        // given
        UseCancelRequest request = new UseCancelRequest(
            ACCOUNT_ID,
            " ",
            amount("100"),
            "use cancel"
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
            .thenReturn(USE_CANCEL_TRANSACTION_ID);
    }

    private void givenBaseData() {
        givenAccount(AccountStatus.ACTIVE);
        givenUseTransaction();
        givenUsage();
        givenPointPolicy();
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

    private void givenUseTransaction() {
        when(transactionRepository.findByPointKeyAndTransactionType(USE_POINT_KEY, TransactionType.USE))
            .thenReturn(Optional.of(useTransaction));
    }

    private void givenUsage() {
        when(usageRepository.findByTransactionIdForUpdate(USE_TRANSACTION_ID)).thenReturn(Optional.of(usage));
    }

    private void givenPointPolicy() {
        PointPolicy policy = persisted(pointPolicy(), POINT_POLICY_ID);
        when(pointPolicyRepository.findByPolicyCodeAndStatus("DEFAULT", PolicyStatus.ACTIVE))
            .thenReturn(Optional.of(policy));
    }

    private PointPolicy pointPolicy() {
        return PointPolicy.builder()
            .policyCode("DEFAULT")
            .name("기본 포인트 정책")
            .maxEarnAmount(amount("100000"))
            .defaultExpireDays(365)
            .minExpireDays(1)
            .maxExpireDays(1824)
            .status(PolicyStatus.ACTIVE)
            .statusUpdatedAt(STATUS_UPDATED_AT)
            .statusUpdatedBy("SYSTEM")
            .build();
    }

    private void givenCancelableAllocations() {
        when(allocationRepository.findCancelableAllocationsForUpdate(USAGE_ID))
            .thenReturn(List.of(expiredAllocation(), activeAllocation()));
        when(earnRepository.findByIdForUpdate(EXPIRED_EARN_ID)).thenReturn(Optional.of(expiredEarn()));
        when(earnRepository.findByIdForUpdate(ACTIVE_EARN_ID)).thenReturn(Optional.of(activeEarn()));
    }

    private void givenUseCancelWillBeSaved(String balanceAfter) {
        givenPointKeys();
        givenTransactionsSaved();
        givenUsageCancelSaved();
        givenReEarnSaved();
        givenOriginalEarnRestored();
        givenAllocationsUpdated();
        givenCancelAllocationsSaved();
        givenUsageUpdated();
        givenBalanceUpdated(balanceAfter);
        givenLedgersSaved();
        givenExistingUseCancelResult();
    }

    private void givenPointKeys() {
        when(pointKeyGenerator.generate()).thenReturn(USE_CANCEL_POINT_KEY, RE_EARN_POINT_KEY);
    }

    private void givenTransactionsSaved() {
        when(transactionRepository.save(any(PointTransaction.class))).thenAnswer(invocation -> {
            PointTransaction transaction = invocation.getArgument(0);
            if (transaction.getTransactionType() == TransactionType.USE_CANCEL) {
                return saveUseCancelTransaction(transaction);
            }
            return saveReEarnTransaction(transaction);
        });
    }

    private PointTransaction saveUseCancelTransaction(PointTransaction transaction) {
        PointTransaction saved = persisted(transaction, USE_CANCEL_TRANSACTION_ID);
        savedUseCancelTransaction.set(saved);
        return saved;
    }

    private PointTransaction saveReEarnTransaction(PointTransaction transaction) {
        PointTransaction saved = persisted(transaction, RE_EARN_TRANSACTION_ID);
        savedReEarnTransaction.set(saved);
        return saved;
    }

    private void givenUsageCancelSaved() {
        when(usageCancelRepository.save(any(PointUsageCancel.class))).thenAnswer(invocation -> {
            PointUsageCancel usageCancel = persisted(invocation.getArgument(0), USAGE_CANCEL_ID);
            savedUsageCancel.set(usageCancel);
            return usageCancel;
        });
    }

    private void givenReEarnSaved() {
        when(earnRepository.save(any(PointEarn.class))).thenAnswer(invocation -> {
            PointEarn earn = persisted(invocation.getArgument(0), NEW_EARN_ID);
            savedReEarn.set(earn);
            return earn;
        });
    }

    private void givenOriginalEarnRestored() {
        when(earnRepository.restoreEarn(ACTIVE_EARN_ID, ACCOUNT_ID, amount("400"), amount("100"), EarnStatus.AVAILABLE))
            .thenReturn(1);
    }

    private void givenAllocationsUpdated() {
        when(allocationRepository.updateCancelState(EXPIRED_ALLOCATION_ID, amount("1000"), UsageAllocationStatus.CANCELED))
            .thenReturn(1);
        when(allocationRepository.updateCancelState(ACTIVE_ALLOCATION_ID, amount("100"), UsageAllocationStatus.PARTIAL_CANCELED))
            .thenReturn(1);
    }

    private void givenCancelAllocationsSaved() {
        AtomicLong id = new AtomicLong(300L);
        when(cancelAllocationRepository.save(any(PointUsageCancelAllocation.class))).thenAnswer(invocation -> {
            PointUsageCancelAllocation allocation = persisted(invocation.getArgument(0), id.getAndIncrement());
            savedCancelAllocations.add(allocation);
            return allocation;
        });
    }

    private void givenUsageUpdated() {
        when(usageRepository.updateCancelState(USAGE_ID, ACCOUNT_ID, amount("1100"), UsageStatus.PARTIAL_CANCELED))
            .thenReturn(1);
    }

    private void givenBalanceUpdated(String balanceAfter) {
        when(balanceRepository.updateBalanceAmount(ACCOUNT_ID, amount(balanceAfter))).thenReturn(1);
    }

    private void givenLedgersSaved() {
        AtomicLong id = new AtomicLong(400L);
        when(ledgerRepository.save(any(PointLedger.class))).thenAnswer(invocation -> {
            PointLedger ledger = persisted(invocation.getArgument(0), id.getAndIncrement());
            savedLedgers.add(ledger);
            return ledger;
        });
    }

    private void givenExistingUseCancelResult() {
        PointTransaction cancelTransaction = savedUseCancelTransaction.get() == null
            ? useCancelTransaction()
            : savedUseCancelTransaction.get();
        PointUsageCancel usageCancel = savedUsageCancel.get() == null
            ? usageCancel()
            : savedUsageCancel.get();
        List<PointUsageCancelAllocation> allocations = savedCancelAllocations.isEmpty()
            ? cancelAllocations()
            : savedCancelAllocations;
        List<PointLedger> ledgers = savedLedgers.isEmpty() ? ledgers() : savedLedgers;

        when(transactionRepository.findById(USE_CANCEL_TRANSACTION_ID)).thenReturn(Optional.of(cancelTransaction));
        when(transactionRepository.findById(USE_TRANSACTION_ID)).thenReturn(Optional.of(useTransaction));
        when(usageCancelRepository.findByTransactionId(USE_CANCEL_TRANSACTION_ID)).thenReturn(Optional.of(usageCancel));
        when(cancelAllocationRepository.findByUsageCancelIdOrderByIdAsc(USAGE_CANCEL_ID)).thenReturn(allocations);
        when(ledgerRepository.findByUsageCancelIdAndLedgerTypeInOrderByIdAsc(eq(USAGE_CANCEL_ID), any()))
            .thenReturn(ledgers);
    }

    private void verifyNoUseCancelSideEffects() {
        verify(transactionRepository, never()).save(any());
        verify(usageCancelRepository, never()).save(any());
        verify(cancelAllocationRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
        verify(earnRepository, never()).save(any());
        verify(earnRepository, never()).restoreEarn(any(), any(), any(), any(), any());
        verify(allocationRepository, never()).updateCancelState(any(), any(), any());
        verify(usageRepository, never()).updateCancelState(any(), any(), any(), any());
        verify(balanceRepository, never()).updateBalanceAmount(any(), any());
    }

    private void assertCancelRejected(
        UseCancelRequest request,
        ErrorCode errorCode
    ) {
        assertThatThrownBy(() -> useCancelService.cancel(IDEMPOTENCY_KEY, request))
            .isInstanceOf(BaseException.class)
            .extracting("errorCode")
            .isEqualTo(errorCode);

        verifyNoUseCancelSideEffects();
    }

    private UseCancelRequest request(String cancelAmount) {
        return new UseCancelRequest(
            ACCOUNT_ID,
            USE_POINT_KEY,
            amount(cancelAmount),
            "use cancel"
        );
    }

    private PointTransaction useTransaction() {
        return persisted(PointTransaction.builder()
            .pointKey(USE_POINT_KEY)
            .accountId(ACCOUNT_ID)
            .pointUserPolicyId(USER_POLICY_ID)
            .transactionType(TransactionType.USE)
            .amount(amount("1200"))
            .orderNo(ORDER_NO)
            .createdByType(CreatedByType.USER)
            .createdById(String.valueOf(USER_ID))
            .reason("order pay")
            .build(), USE_TRANSACTION_ID);
    }

    private PointTransaction useCancelTransaction() {
        return persisted(PointTransaction.builder()
            .pointKey(USE_CANCEL_POINT_KEY)
            .accountId(ACCOUNT_ID)
            .pointPolicyId(POINT_POLICY_ID)
            .pointUserPolicyId(USER_POLICY_ID)
            .transactionType(TransactionType.USE_CANCEL)
            .amount(amount("1100"))
            .orderNo(ORDER_NO)
            .relatedTransactionId(USE_TRANSACTION_ID)
            .createdByType(CreatedByType.USER)
            .createdById(String.valueOf(USER_ID))
            .reason("use cancel")
            .build(), USE_CANCEL_TRANSACTION_ID);
    }

    private PointUsage usage(
        UsageStatus status,
        String usageAmount,
        String cancelledAmount
    ) {
        return persisted(PointUsage.builder()
            .transactionId(USE_TRANSACTION_ID)
            .accountId(ACCOUNT_ID)
            .orderNo(ORDER_NO)
            .usageAmount(amount(usageAmount))
            .cancelledAmount(amount(cancelledAmount))
            .status(status)
            .build(), USAGE_ID);
    }

    private PointUsageAllocation expiredAllocation() {
        return allocation(EXPIRED_ALLOCATION_ID, EXPIRED_EARN_ID, 1, "1000", "0");
    }

    private PointUsageAllocation activeAllocation() {
        return allocation(ACTIVE_ALLOCATION_ID, ACTIVE_EARN_ID, 2, "200", "0");
    }

    private PointUsageAllocation allocation(
        Long id,
        Long earnId,
        Integer seq,
        String amount,
        String cancelledAmount
    ) {
        return persisted(PointUsageAllocation.builder()
            .usageId(USAGE_ID)
            .earnId(earnId)
            .allocationSeq(seq)
            .amount(amount(amount))
            .cancelledAmount(amount(cancelledAmount))
            .status(UsageAllocationStatus.USED)
            .build(), id);
    }

    private PointEarn expiredEarn() {
        return earn(
            EXPIRED_EARN_ID,
            amount("1000"),
            BigDecimal.ZERO,
            amount("1000"),
            EarnStatus.EXHAUSTED,
            LocalDateTime.now().minusDays(1)
        );
    }

    private PointEarn activeEarn() {
        return earn(
            ACTIVE_EARN_ID,
            amount("500"),
            amount("300"),
            amount("200"),
            EarnStatus.AVAILABLE,
            LocalDateTime.now().plusDays(10)
        );
    }

    private PointEarn earn(
        Long id,
        BigDecimal earnAmount,
        BigDecimal availableAmount,
        BigDecimal consumedAmount,
        EarnStatus status,
        LocalDateTime expiresAt
    ) {
        return persisted(PointEarn.builder()
            .transactionId(id + 1000)
            .accountId(ACCOUNT_ID)
            .earnType(EarnType.NORMAL)
            .earnAmount(earnAmount)
            .availableAmount(availableAmount)
            .consumedAmount(consumedAmount)
            .cancelledAmount(BigDecimal.ZERO)
            .expiredAmount(BigDecimal.ZERO)
            .expiresAt(expiresAt)
            .status(status)
            .build(), id);
    }

    private PointUsageCancel usageCancel() {
        return persisted(PointUsageCancel.builder()
            .transactionId(USE_CANCEL_TRANSACTION_ID)
            .accountId(ACCOUNT_ID)
            .usageId(USAGE_ID)
            .cancelAmount(amount("1100"))
            .build(), USAGE_CANCEL_ID);
    }

    private List<PointUsageCancelAllocation> cancelAllocations() {
        return List.of(
            cancelAllocation(300L, EXPIRED_ALLOCATION_ID, EXPIRED_EARN_ID, NEW_EARN_ID, "1000", RestoreType.NEW_EARN),
            cancelAllocation(301L, ACTIVE_ALLOCATION_ID, ACTIVE_EARN_ID, ACTIVE_EARN_ID, "100", RestoreType.ORIGINAL_EARN)
        );
    }

    private PointUsageCancelAllocation cancelAllocation(
        Long id,
        Long usageAllocationId,
        Long originalEarnId,
        Long restoredEarnId,
        String cancelAmount,
        RestoreType restoreType
    ) {
        return persisted(PointUsageCancelAllocation.builder()
            .usageCancelId(USAGE_CANCEL_ID)
            .usageAllocationId(usageAllocationId)
            .originalEarnId(originalEarnId)
            .restoredEarnId(restoredEarnId)
            .cancelAmount(amount(cancelAmount))
            .restoreType(restoreType)
            .build(), id);
    }

    private List<PointLedger> ledgers() {
        return List.of(
            ledger(400L, NEW_EARN_ID, EXPIRED_ALLOCATION_ID, LedgerType.USE_CANCEL_NEW_EARN_INCREASE, "1000", "300", "1300", "0", "1000"),
            ledger(401L, ACTIVE_EARN_ID, ACTIVE_ALLOCATION_ID, LedgerType.USE_CANCEL_ORIGINAL_INCREASE, "100", "1300", "1400", "300", "400")
        );
    }

    private PointLedger ledger(
        Long id,
        Long earnId,
        Long usageAllocationId,
        LedgerType ledgerType,
        String delta,
        String balanceBefore,
        String balanceAfter,
        String earnBefore,
        String earnAfter
    ) {
        return persisted(PointLedger.builder()
            .transactionId(ledgerTransactionId(ledgerType))
            .accountId(ACCOUNT_ID)
            .earnId(earnId)
            .usageId(USAGE_ID)
            .usageCancelId(USAGE_CANCEL_ID)
            .usageAllocationId(usageAllocationId)
            .ledgerType(ledgerType)
            .deltaAmount(amount(delta))
            .accountBalanceBefore(amount(balanceBefore))
            .accountBalanceAfter(amount(balanceAfter))
            .earnAvailableBefore(amount(earnBefore))
            .earnAvailableAfter(amount(earnAfter))
            .description("포인트 사용취소")
            .build(), id);
    }

    private Long ledgerTransactionId(LedgerType ledgerType) {
        return ledgerType == LedgerType.USE_CANCEL_NEW_EARN_INCREASE
            ? RE_EARN_TRANSACTION_ID
            : USE_CANCEL_TRANSACTION_ID;
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
