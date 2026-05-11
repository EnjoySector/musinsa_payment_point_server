package musinsa.pay.point_server.application.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import musinsa.pay.point_server.application.expire.ExpirePointProcessor;
import musinsa.pay.point_server.application.expire.ExpirePointResult;
import musinsa.pay.point_server.application.idempotency.IdempotencyService;
import musinsa.pay.point_server.application.idempotency.IdempotentOperation;
import musinsa.pay.point_server.application.idempotency.IdempotentRequest;
import musinsa.pay.point_server.domain.account.PointBalance;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PointCommandTemplate")
class PointCommandTemplateTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final String IDEMPOTENCY_KEY = "request-001";

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private ExpirePointProcessor expirePointProcessor;

    private PointCommandTemplate pointCommandTemplate;

    @BeforeEach
    void setUp() {
        pointCommandTemplate = new PointCommandTemplate(idempotencyService, expirePointProcessor);
    }

    @Test
    @DisplayName("새 요청이면 만료 정리 후 최신 잔액으로 실제 처리를 실행한다")
    void expireBeforeOperationForNewRequest() {
        // given
        PointBalance balance = balance("1500");
        IdempotentOperation operation = mock(IdempotentOperation.class);
        IdempotentRequest request = request();

        givenNewIdempotentRequestExecutesWith(balance, request);
        when(expirePointProcessor.expire(balance))
            .thenReturn(expireResult("1500", "300", "1200"));
        when(operation.execute(any(PointBalance.class))).thenReturn(10L);

        // when
        Long transactionId = pointCommandTemplate.execute(request, operation);

        // then
        assertThat(transactionId).isEqualTo(10L);

        InOrder inOrder = inOrder(expirePointProcessor, operation);
        inOrder.verify(expirePointProcessor).expire(balance);

        ArgumentCaptor<PointBalance> captor = ArgumentCaptor.forClass(PointBalance.class);
        inOrder.verify(operation).execute(captor.capture());
        assertThat(captor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(captor.getValue().getBalanceAmount()).isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("만료 대상이 없으면 락을 잡은 잔액 객체를 그대로 실제 처리에 넘긴다")
    void useLockedBalanceWhenNoExpiredAmount() {
        // given
        PointBalance balance = balance("1500");
        IdempotentOperation operation = mock(IdempotentOperation.class);
        IdempotentRequest request = request();

        givenNewIdempotentRequestExecutesWith(balance, request);
        when(expirePointProcessor.expire(balance))
            .thenReturn(ExpirePointResult.empty(ACCOUNT_ID, amount("1500")));
        when(operation.execute(same(balance))).thenReturn(10L);

        // when
        Long transactionId = pointCommandTemplate.execute(request, operation);

        // then
        assertThat(transactionId).isEqualTo(10L);

        InOrder inOrder = inOrder(expirePointProcessor, operation);
        inOrder.verify(expirePointProcessor).expire(balance);
        inOrder.verify(operation).execute(balance);
    }

    @Test
    @DisplayName("멱등 재호출이면 만료 정리와 실제 처리를 실행하지 않고 기존 거래를 반환한다")
    void returnExistingTransactionWithoutExpire() {
        // given
        IdempotentOperation operation = mock(IdempotentOperation.class);
        IdempotentRequest request = request();

        when(idempotencyService.executeOrReturn(eq(request), any()))
            .thenReturn(10L);

        // when
        Long transactionId = pointCommandTemplate.execute(request, operation);

        // then
        assertThat(transactionId).isEqualTo(10L);
        verifyNoInteractions(expirePointProcessor, operation);
    }

    private void givenNewIdempotentRequestExecutesWith(
        PointBalance balance,
        IdempotentRequest request
    ) {
        when(idempotencyService.executeOrReturn(eq(request), any()))
            .thenAnswer(invocation -> invocation
                .<IdempotentOperation>getArgument(1)
                .execute(balance));
    }

    private IdempotentRequest request() {
        return new IdempotentRequest(
            ACCOUNT_ID,
            TransactionType.EARN,
            IDEMPOTENCY_KEY,
            Map.of("amount", "1000")
        );
    }

    private ExpirePointResult expireResult(
        String balanceBefore,
        String balanceAfter,
        String expiredAmount
    ) {
        return new ExpirePointResult(
            ACCOUNT_ID,
            amount(balanceBefore),
            amount(balanceAfter),
            amount(expiredAmount),
            List.of()
        );
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
