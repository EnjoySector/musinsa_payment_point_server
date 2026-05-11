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
import musinsa.pay.point_server.application.usage.dto.UsePointRequest;
import musinsa.pay.point_server.application.usage.dto.UsePointResponse;
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
import musinsa.pay.point_server.domain.transaction.CreatedByType;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import musinsa.pay.point_server.domain.usage.PointUsage;
import musinsa.pay.point_server.domain.usage.PointUsageAllocation;
import musinsa.pay.point_server.domain.usage.UsageAllocationStatus;
import musinsa.pay.point_server.domain.usage.UsageStatus;
import musinsa.pay.point_server.persistence.account.PointAccountRepository;
import musinsa.pay.point_server.persistence.account.PointBalanceRepository;
import musinsa.pay.point_server.persistence.earn.PointEarnRepository;
import musinsa.pay.point_server.persistence.ledger.PointLedgerRepository;
import musinsa.pay.point_server.persistence.transaction.PointTransactionRepository;
import musinsa.pay.point_server.persistence.usage.PointUsageAllocationRepository;
import musinsa.pay.point_server.persistence.usage.PointUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("포인트 사용 서비스")
class UsePointServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final Long USER_POLICY_ID = 200L;
    private static final Long TRANSACTION_ID = 10L;
    private static final Long USAGE_ID = 20L;
    private static final Long MANUAL_EARN_ID = 101L;
    private static final Long EARLY_EARN_ID = 102L;
    private static final Long LATE_EARN_ID = 103L;
    private static final String IDEMPOTENCY_KEY = "use-request-001";
    private static final String POINT_KEY = "260511USE000000001";
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
    private PointEarnRepository earnRepository;

    @Mock
    private PointUsageRepository usageRepository;

    @Mock
    private PointUsageAllocationRepository allocationRepository;

    @Mock
    private PointLedgerRepository ledgerRepository;

    private UsePointService usePointService;
    private PointBalance balance;
    private AtomicReference<PointTransaction> savedTransaction;
    private AtomicReference<PointUsage> savedUsage;
    private List<PointUsageAllocation> savedAllocations;
    private List<PointLedger> savedLedgers;

    @BeforeEach
    void setUp() {
        UsePointProcessor processor = new UsePointProcessor(
            new UsePointContextFactory(accountRepository),
            new UsePointValidator(usageRepository),
            new PointUsageAllocationCalculator(earnRepository),
            pointKeyGenerator,
            transactionRepository,
            usageRepository,
            allocationRepository,
            earnRepository,
            balanceRepository,
            ledgerRepository
        );

        usePointService = new UsePointService(
            pointCommandTemplate,
            processor
        );

        balance = balance("1500");
        savedTransaction = new AtomicReference<>();
        savedUsage = new AtomicReference<>();
        savedAllocations = new ArrayList<>();
        savedLedgers = new ArrayList<>();
    }

    @Test
    @DisplayName("수기 지급 포인트를 먼저 사용하고 이후 만료일이 빠른 순서로 차감한다")
    void usePointsByManualAndExpiryPriority() {
        // given
        UsePointRequest request = request("1000");
        givenIdempotencyExecutesOperation();
        givenAccount(AccountStatus.ACTIVE);
        givenOrderNotUsed();
        givenUsableEarns(manualEarn(), earlyEarn(), lateEarn());
        givenUseWillBeSaved("500");

        // when
        UsePointResponse response = usePointService.use(IDEMPOTENCY_KEY, request);

        // then
        assertThat(response.pointKey()).isEqualTo(POINT_KEY);
        assertThat(response.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(response.usageId()).isEqualTo(USAGE_ID);
        assertThat(response.orderNo()).isEqualTo(ORDER_NO);
        assertThat(response.amount()).isEqualByComparingTo("1000");
        assertThat(response.balanceBefore()).isEqualByComparingTo("1500");
        assertThat(response.balanceAfter()).isEqualByComparingTo("500");
        assertThat(response.allocations()).extracting("earnId")
            .containsExactly(MANUAL_EARN_ID, EARLY_EARN_ID, LATE_EARN_ID);
        assertThat(response.allocations()).extracting("amount")
            .containsExactly(amount("300"), amount("500"), amount("200"));

        verify(earnRepository).useEarn(MANUAL_EARN_ID, ACCOUNT_ID, BigDecimal.ZERO, amount("300"), EarnStatus.EXHAUSTED);
        verify(earnRepository).useEarn(EARLY_EARN_ID, ACCOUNT_ID, BigDecimal.ZERO, amount("500"), EarnStatus.EXHAUSTED);
        verify(earnRepository).useEarn(LATE_EARN_ID, ACCOUNT_ID, amount("800"), amount("200"), EarnStatus.AVAILABLE);
        verify(balanceRepository).updateBalanceAmount(ACCOUNT_ID, amount("500"));
    }

    @Test
    @DisplayName("계정 잔액이 부족하면 사용하지 않는다")
    void rejectNotEnoughBalance() {
        // given
        balance = balance("400");
        UsePointRequest request = request("500");
        givenIdempotencyExecutesOperation();
        givenAccount(AccountStatus.ACTIVE);

        // when
        // then
        assertUseRejected(request, ErrorCode.POINT_BALANCE_NOT_ENOUGH);
        verify(earnRepository, never()).findUsableEarnsForUpdate(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("사용 가능 적립 합계가 부족하면 사용하지 않는다")
    void rejectNotEnoughUsableEarns() {
        // given
        UsePointRequest request = request("1000");
        givenIdempotencyExecutesOperation();
        givenAccount(AccountStatus.ACTIVE);
        givenOrderNotUsed();
        givenUsableEarns(manualEarn());

        // when
        // then
        assertUseRejected(request, ErrorCode.POINT_BALANCE_NOT_ENOUGH);
    }

    @Test
    @DisplayName("이미 포인트를 사용한 주문이면 중복 사용하지 않는다")
    void rejectDuplicateOrder() {
        // given
        UsePointRequest request = request("500");
        givenIdempotencyExecutesOperation();
        givenAccount(AccountStatus.ACTIVE);
        givenOrderAlreadyUsed();

        // when
        // then
        assertUseRejected(request, ErrorCode.POINT_USAGE_DUPLICATE_ORDER);
    }

    @Test
    @DisplayName("0원 사용 요청이면 사용하지 않는다")
    void rejectZeroUseAmount() {
        // given
        UsePointRequest request = request("0");
        givenIdempotencyExecutesOperation();
        givenAccount(AccountStatus.ACTIVE);

        // when
        // then
        assertUseRejected(request, ErrorCode.POINT_AMOUNT_INVALID);
    }

    @Test
    @DisplayName("소수점 사용 요청이면 사용하지 않는다")
    void rejectDecimalUseAmount() {
        // given
        UsePointRequest request = request("10.5");
        givenIdempotencyExecutesOperation();
        givenAccount(AccountStatus.ACTIVE);

        // when
        // then
        assertUseRejected(request, ErrorCode.POINT_AMOUNT_INVALID);
    }

    @Test
    @DisplayName("차단 계정이면 사용하지 않는다")
    void rejectBlockedAccount() {
        // given
        UsePointRequest request = request("500");
        givenIdempotencyExecutesOperation();
        givenAccount(AccountStatus.BLOCKED);

        // when
        // then
        assertUseRejected(request, ErrorCode.ACCOUNT_BLOCKED);
    }

    @Test
    @DisplayName("같은 멱등성 키 재호출이면 기존 사용 거래 결과를 반환한다")
    void returnExistingUseResultByIdempotency() {
        // given
        UsePointRequest request = request("1000");
        givenIdempotencyReturnsExistingTransactionId();
        givenExistingUseResult();

        // when
        UsePointResponse response = usePointService.use(IDEMPOTENCY_KEY, request);

        // then
        assertThat(response.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(response.usageId()).isEqualTo(USAGE_ID);
        assertThat(response.balanceBefore()).isEqualByComparingTo("1500");
        assertThat(response.balanceAfter()).isEqualByComparingTo("500");
        verifyNoUseSideEffects();
    }

    @Test
    @DisplayName("필수 요청값이 없으면 멱등성 처리 전에 거절한다")
    void rejectInvalidRequestBeforeIdempotency() {
        // given
        UsePointRequest request = new UsePointRequest(
            ACCOUNT_ID,
            " ",
            amount("1000"),
            "order pay"
        );

        // when
        assertUseRejected(request, ErrorCode.INVALID_REQUEST);

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
            .thenReturn(TRANSACTION_ID);
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

    private void givenOrderNotUsed() {
        when(usageRepository.existsByAccountIdAndOrderNo(ACCOUNT_ID, ORDER_NO)).thenReturn(false);
    }

    private void givenOrderAlreadyUsed() {
        when(usageRepository.existsByAccountIdAndOrderNo(ACCOUNT_ID, ORDER_NO)).thenReturn(true);
    }

    private void givenUsableEarns(PointEarn... earns) {
        when(earnRepository.findUsableEarnsForUpdate(
            eq(ACCOUNT_ID),
            eq(EarnStatus.AVAILABLE),
            eq(BigDecimal.ZERO),
            any(LocalDateTime.class),
            eq(EarnType.MANUAL)
        )).thenReturn(List.of(earns));
    }

    private void givenUseWillBeSaved(String balanceAfter) {
        givenPointKey();
        givenTransactionSaved();
        givenUsageSaved();
        givenAllocationsSaved();
        givenEarnsUpdated();
        givenBalanceUpdated(balanceAfter);
        givenLedgersSaved();
        givenExistingUseResult();
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

    private void givenUsageSaved() {
        when(usageRepository.save(any(PointUsage.class))).thenAnswer(invocation -> {
            PointUsage usage = persisted(invocation.getArgument(0), USAGE_ID);
            savedUsage.set(usage);
            return usage;
        });
    }

    private void givenAllocationsSaved() {
        AtomicLong allocationId = new AtomicLong(1000L);
        when(allocationRepository.save(any(PointUsageAllocation.class))).thenAnswer(invocation -> {
            PointUsageAllocation allocation = persisted(invocation.getArgument(0), allocationId.getAndIncrement());
            savedAllocations.add(allocation);
            return allocation;
        });
    }

    private void givenEarnsUpdated() {
        when(earnRepository.useEarn(any(), any(), any(), any(), any())).thenReturn(1);
    }

    private void givenBalanceUpdated(String balanceAfter) {
        when(balanceRepository.updateBalanceAmount(ACCOUNT_ID, amount(balanceAfter))).thenReturn(1);
    }

    private void givenLedgersSaved() {
        AtomicLong ledgerId = new AtomicLong(2000L);
        when(ledgerRepository.save(any(PointLedger.class))).thenAnswer(invocation -> {
            PointLedger ledger = persisted(invocation.getArgument(0), ledgerId.getAndIncrement());
            savedLedgers.add(ledger);
            return ledger;
        });
    }

    private void givenExistingUseResult() {
        PointTransaction transaction = savedTransaction.get() == null ? transaction() : savedTransaction.get();
        PointUsage usage = savedUsage.get() == null ? usage() : savedUsage.get();
        List<PointUsageAllocation> allocations = savedAllocations.isEmpty() ? allocations() : savedAllocations;
        List<PointLedger> ledgers = savedLedgers.isEmpty() ? ledgers() : savedLedgers;

        when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(transaction));
        when(usageRepository.findByTransactionId(TRANSACTION_ID)).thenReturn(Optional.of(usage));
        when(allocationRepository.findByUsageIdOrderByAllocationSeqAsc(USAGE_ID)).thenReturn(allocations);
        when(ledgerRepository.findByTransactionIdAndLedgerTypeOrderByIdAsc(TRANSACTION_ID, LedgerType.USE_DECREASE))
            .thenReturn(ledgers);
    }

    private void verifyNoUseSideEffects() {
        verify(transactionRepository, never()).save(any());
        verify(usageRepository, never()).save(any());
        verify(allocationRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
        verify(earnRepository, never()).useEarn(any(), any(), any(), any(), any());
        verify(balanceRepository, never()).updateBalanceAmount(any(), any());
    }

    private void assertUseRejected(
        UsePointRequest request,
        ErrorCode errorCode
    ) {
        assertThatThrownBy(() -> usePointService.use(IDEMPOTENCY_KEY, request))
            .isInstanceOf(BaseException.class)
            .extracting("errorCode")
            .isEqualTo(errorCode);

        verifyNoUseSideEffects();
    }

    private UsePointRequest request(String amount) {
        return new UsePointRequest(
            ACCOUNT_ID,
            ORDER_NO,
            amount(amount),
            "order pay"
        );
    }

    private PointTransaction transaction() {
        return persisted(PointTransaction.builder()
            .pointKey(POINT_KEY)
            .accountId(ACCOUNT_ID)
            .pointUserPolicyId(USER_POLICY_ID)
            .transactionType(TransactionType.USE)
            .amount(amount("1000"))
            .orderNo(ORDER_NO)
            .createdByType(CreatedByType.USER)
            .createdById(String.valueOf(USER_ID))
            .reason("order pay")
            .build(), TRANSACTION_ID);
    }

    private PointUsage usage() {
        return persisted(PointUsage.builder()
            .transactionId(TRANSACTION_ID)
            .accountId(ACCOUNT_ID)
            .orderNo(ORDER_NO)
            .usageAmount(amount("1000"))
            .cancelledAmount(BigDecimal.ZERO)
            .status(UsageStatus.USED)
            .build(), USAGE_ID);
    }

    private List<PointUsageAllocation> allocations() {
        return List.of(
            allocation(1000L, MANUAL_EARN_ID, 1, "300"),
            allocation(1001L, EARLY_EARN_ID, 2, "500"),
            allocation(1002L, LATE_EARN_ID, 3, "200")
        );
    }

    private PointUsageAllocation allocation(
        Long id,
        Long earnId,
        Integer seq,
        String amount
    ) {
        return persisted(PointUsageAllocation.builder()
            .usageId(USAGE_ID)
            .earnId(earnId)
            .allocationSeq(seq)
            .amount(amount(amount))
            .cancelledAmount(BigDecimal.ZERO)
            .status(UsageAllocationStatus.USED)
            .build(), id);
    }

    private List<PointLedger> ledgers() {
        return List.of(
            ledger(2000L, MANUAL_EARN_ID, 1000L, "300", "1500", "1200", "300", "0"),
            ledger(2001L, EARLY_EARN_ID, 1001L, "500", "1200", "700", "500", "0"),
            ledger(2002L, LATE_EARN_ID, 1002L, "200", "700", "500", "1000", "800")
        );
    }

    private PointLedger ledger(
        Long id,
        Long earnId,
        Long allocationId,
        String delta,
        String balanceBefore,
        String balanceAfter,
        String earnBefore,
        String earnAfter
    ) {
        return persisted(PointLedger.builder()
            .transactionId(TRANSACTION_ID)
            .accountId(ACCOUNT_ID)
            .earnId(earnId)
            .usageId(USAGE_ID)
            .usageAllocationId(allocationId)
            .ledgerType(LedgerType.USE_DECREASE)
            .deltaAmount(amount(delta).negate())
            .accountBalanceBefore(amount(balanceBefore))
            .accountBalanceAfter(amount(balanceAfter))
            .earnAvailableBefore(amount(earnBefore))
            .earnAvailableAfter(amount(earnAfter))
            .description("포인트 사용")
            .build(), id);
    }

    private PointEarn manualEarn() {
        return earn(MANUAL_EARN_ID, EarnType.MANUAL, "300", "300", "0", LocalDateTime.now().plusDays(20));
    }

    private PointEarn earlyEarn() {
        return earn(EARLY_EARN_ID, EarnType.NORMAL, "500", "500", "0", LocalDateTime.now().plusDays(3));
    }

    private PointEarn lateEarn() {
        return earn(LATE_EARN_ID, EarnType.NORMAL, "1000", "1000", "0", LocalDateTime.now().plusDays(10));
    }

    private PointEarn earn(
        Long id,
        EarnType earnType,
        String earnAmount,
        String availableAmount,
        String consumedAmount,
        LocalDateTime expiresAt
    ) {
        return persisted(PointEarn.builder()
            .transactionId(id + 1000)
            .accountId(ACCOUNT_ID)
            .earnType(earnType)
            .earnAmount(amount(earnAmount))
            .availableAmount(amount(availableAmount))
            .consumedAmount(amount(consumedAmount))
            .cancelledAmount(BigDecimal.ZERO)
            .expiredAmount(BigDecimal.ZERO)
            .expiresAt(expiresAt)
            .status(EarnStatus.AVAILABLE)
            .build(), id);
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
