package musinsa.pay.point_server.application.expire;

import static musinsa.pay.point_server.support.EntityTestSupport.persisted;
import static org.assertj.core.api.Assertions.assertThat;
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
import musinsa.pay.point_server.application.expire.dto.ExpirePointResponse;
import musinsa.pay.point_server.common.generator.PointKeyGenerator;
import musinsa.pay.point_server.domain.account.PointBalance;
import musinsa.pay.point_server.domain.earn.EarnStatus;
import musinsa.pay.point_server.domain.earn.EarnType;
import musinsa.pay.point_server.domain.earn.PointEarn;
import musinsa.pay.point_server.domain.ledger.LedgerType;
import musinsa.pay.point_server.domain.ledger.PointLedger;
import musinsa.pay.point_server.domain.transaction.CreatedByType;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import musinsa.pay.point_server.persistence.account.PointBalanceRepository;
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
@DisplayName("포인트 만료 처리 서비스")
class ExpirePointServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long POINT_POLICY_ID = 100L;
    private static final Long USER_POLICY_ID = 200L;
    private static final Long FIRST_EARN_ID = 10L;
    private static final Long SECOND_EARN_ID = 11L;
    private static final Long FIRST_EARN_TRANSACTION_ID = 100L;
    private static final Long SECOND_EARN_TRANSACTION_ID = 101L;
    private static final String FIRST_EXPIRE_POINT_KEY = "260511EXP000000001";
    private static final String SECOND_EXPIRE_POINT_KEY = "260511EXP000000002";

    @Mock
    private PointBalanceRepository balanceRepository;

    @Mock
    private PointEarnRepository earnRepository;

    @Mock
    private PointTransactionRepository transactionRepository;

    @Mock
    private PointLedgerRepository ledgerRepository;

    @Mock
    private PointKeyGenerator pointKeyGenerator;

    private ExpirePointService expirePointService;
    private PointBalance balance;
    private List<PointTransaction> savedTransactions;
    private List<PointLedger> savedLedgers;

    @BeforeEach
    void setUp() {
        ExpirePointProcessor processor = new ExpirePointProcessor(
            pointKeyGenerator,
            balanceRepository,
            earnRepository,
            transactionRepository,
            ledgerRepository
        );
        expirePointService = new ExpirePointService(balanceRepository, processor);
        balance = PointBalance.builder()
            .accountId(ACCOUNT_ID)
            .balanceAmount(amount("1500"))
            .build();
        savedTransactions = new ArrayList<>();
        savedLedgers = new ArrayList<>();
    }

    @Test
    @DisplayName("만료 대상 적립을 잔액에서 차감하고 EXPIRE 거래와 원장을 남긴다")
    void expirePoints() {
        // given
        givenBalance();
        givenExpiredEarns(firstEarn(), secondEarn());
        givenOriginTransactions();
        givenPointKeys();
        givenExpireTransactionsSaved();
        givenEarnsExpired();
        givenBalanceUpdated();
        givenLedgersSaved();

        // when
        ExpirePointResponse response = expirePointService.expire(ACCOUNT_ID);

        // then
        assertThat(response.expiredCount()).isEqualTo(2);
        assertThat(response.expiredAmount()).isEqualByComparingTo("1200");
        assertThat(response.balanceBefore()).isEqualByComparingTo("1500");
        assertThat(response.balanceAfter()).isEqualByComparingTo("300");

        assertThat(response.expiredEarns()).extracting("earnId")
            .containsExactly(FIRST_EARN_ID, SECOND_EARN_ID);
        assertThat(response.expiredEarns()).extracting("expiredAmount")
            .containsExactly(amount("1000"), amount("200"));

        assertThat(savedTransactions).extracting("transactionType")
            .containsExactly(TransactionType.EXPIRE, TransactionType.EXPIRE);
        assertThat(savedTransactions).extracting("relatedTransactionId")
            .containsExactly(FIRST_EARN_TRANSACTION_ID, SECOND_EARN_TRANSACTION_ID);
        assertThat(savedLedgers).extracting("ledgerType")
            .containsExactly(LedgerType.EXPIRE_DECREASE, LedgerType.EXPIRE_DECREASE);
        assertThat(savedLedgers).extracting("accountBalanceBefore")
            .containsExactly(amount("1500"), amount("500"));
        assertThat(savedLedgers).extracting("accountBalanceAfter")
            .containsExactly(amount("500"), amount("300"));

        verify(earnRepository).expireEarn(
            FIRST_EARN_ID,
            ACCOUNT_ID,
            BigDecimal.ZERO,
            amount("1000"),
            EarnStatus.EXPIRED,
            EarnStatus.AVAILABLE,
            amount("1000")
        );
        verify(earnRepository).expireEarn(
            SECOND_EARN_ID,
            ACCOUNT_ID,
            BigDecimal.ZERO,
            amount("200"),
            EarnStatus.EXPIRED,
            EarnStatus.AVAILABLE,
            amount("200")
        );
        verify(balanceRepository).updateBalanceAmount(ACCOUNT_ID, amount("300"));
    }

    @Test
    @DisplayName("만료 대상이 없으면 잔액과 거래 데이터를 변경하지 않는다")
    void returnEmptyResultWhenNoExpiredEarns() {
        // given
        givenBalance();
        givenExpiredEarns();

        // when
        ExpirePointResponse response = expirePointService.expire(ACCOUNT_ID);

        // then
        assertThat(response.expiredCount()).isZero();
        assertThat(response.expiredAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.balanceBefore()).isEqualByComparingTo("1500");
        assertThat(response.balanceAfter()).isEqualByComparingTo("1500");
        assertThat(balance.getBalanceAmount()).isEqualByComparingTo("1500");

        verify(pointKeyGenerator, never()).generate();
        verify(transactionRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
        verify(earnRepository, never()).expireEarn(any(), any(), any(), any(), any(), any(), any());
        verify(balanceRepository, never()).updateBalanceAmount(any(), any());
    }

    private void givenBalance() {
        when(balanceRepository.findByAccountIdForUpdate(ACCOUNT_ID))
            .thenReturn(Optional.of(balance));
    }

    private void givenExpiredEarns(PointEarn... earns) {
        when(earnRepository.findExpiredEarnsForUpdate(
            eq(ACCOUNT_ID),
            eq(EarnStatus.AVAILABLE),
            eq(BigDecimal.ZERO),
            any(LocalDateTime.class)
        )).thenReturn(List.of(earns));
    }

    private void givenOriginTransactions() {
        when(transactionRepository.findAllById(any()))
            .thenReturn(List.of(
                originTransaction(FIRST_EARN_TRANSACTION_ID, "260511ERN000000001", "1000"),
                originTransaction(SECOND_EARN_TRANSACTION_ID, "260511ERN000000002", "500")
            ));
    }

    private void givenPointKeys() {
        when(pointKeyGenerator.generate())
            .thenReturn(FIRST_EXPIRE_POINT_KEY, SECOND_EXPIRE_POINT_KEY);
    }

    private void givenExpireTransactionsSaved() {
        AtomicLong id = new AtomicLong(200L);
        when(transactionRepository.save(any(PointTransaction.class))).thenAnswer(invocation -> {
            PointTransaction transaction = persisted(invocation.getArgument(0), id.getAndIncrement());
            savedTransactions.add(transaction);
            return transaction;
        });
    }

    private void givenEarnsExpired() {
        when(earnRepository.expireEarn(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(1);
    }

    private void givenBalanceUpdated() {
        when(balanceRepository.updateBalanceAmount(ACCOUNT_ID, amount("300"))).thenReturn(1);
    }

    private void givenLedgersSaved() {
        AtomicLong id = new AtomicLong(300L);
        when(ledgerRepository.save(any(PointLedger.class))).thenAnswer(invocation -> {
            PointLedger ledger = persisted(invocation.getArgument(0), id.getAndIncrement());
            savedLedgers.add(ledger);
            return ledger;
        });
    }

    private PointEarn firstEarn() {
        return earn(FIRST_EARN_ID, FIRST_EARN_TRANSACTION_ID, "1000", "1000", "0");
    }

    private PointEarn secondEarn() {
        return earn(SECOND_EARN_ID, SECOND_EARN_TRANSACTION_ID, "500", "200", "300");
    }

    private PointEarn earn(
        Long earnId,
        Long transactionId,
        String earnAmount,
        String availableAmount,
        String consumedAmount
    ) {
        return persisted(PointEarn.builder()
            .transactionId(transactionId)
            .accountId(ACCOUNT_ID)
            .earnType(EarnType.NORMAL)
            .earnAmount(amount(earnAmount))
            .availableAmount(amount(availableAmount))
            .consumedAmount(amount(consumedAmount))
            .cancelledAmount(BigDecimal.ZERO)
            .expiredAmount(BigDecimal.ZERO)
            .expiresAt(LocalDateTime.now().minusDays(1))
            .status(EarnStatus.AVAILABLE)
            .build(), earnId);
    }

    private PointTransaction originTransaction(
        Long transactionId,
        String pointKey,
        String amount
    ) {
        return persisted(PointTransaction.builder()
            .pointKey(pointKey)
            .accountId(ACCOUNT_ID)
            .pointPolicyId(POINT_POLICY_ID)
            .pointUserPolicyId(USER_POLICY_ID)
            .transactionType(TransactionType.EARN)
            .amount(amount(amount))
            .createdByType(CreatedByType.USER)
            .createdById("1")
            .reason("earn")
            .build(), transactionId);
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
