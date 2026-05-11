package musinsa.pay.point_server.application.idempotency;

import static musinsa.pay.point_server.support.EntityTestSupport.persisted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.account.PointBalance;
import musinsa.pay.point_server.domain.idempotency.IdempotencyStatus;
import musinsa.pay.point_server.domain.idempotency.PointIdempotency;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import musinsa.pay.point_server.persistence.account.PointBalanceRepository;
import musinsa.pay.point_server.persistence.idempotency.PointIdempotencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("멱등성 처리 서비스")
class IdempotencyServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final String IDEMPOTENCY_KEY = "request-001";

    @Mock
    private PointIdempotencyRepository idempotencyRepository;

    @Mock
    private PointBalanceRepository balanceRepository;

    private RequestHashGenerator requestHashGenerator;
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        requestHashGenerator = new RequestHashGenerator(new ObjectMapper());
        idempotencyService = new IdempotencyService(
            idempotencyRepository,
            balanceRepository,
            requestHashGenerator
        );
    }

    @Test
    @DisplayName("처음 들어온 요청이면 멱등성 레코드를 생성하고 성공 상태로 변경한다")
    void executeNewRequestAndMarkSuccess() {
        // given
        PointBalance balance = mock(PointBalance.class);
        IdempotentRequest request = request(Map.of("amount", "1000"));

        when(balanceRepository.findByAccountIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(balance));
        when(idempotencyRepository.findByKeyForUpdate(ACCOUNT_ID, TransactionType.EARN, IDEMPOTENCY_KEY))
            .thenReturn(Optional.empty());
        when(idempotencyRepository.save(any(PointIdempotency.class))).thenAnswer(invocation -> {
            PointIdempotency record = invocation.getArgument(0);
            return persisted(record, 1L);
        });
        when(idempotencyRepository.markSuccess(1L, 10L, IdempotencyStatus.SUCCESS, IdempotencyStatus.PROCESSING))
            .thenReturn(1);

        // when
        Long transactionId = idempotencyService.executeOrReturn(request, lockedBalance -> {
            assertThat(lockedBalance).isSameAs(balance);
            return 10L;
        });

        // then
        ArgumentCaptor<PointIdempotency> captor = ArgumentCaptor.forClass(PointIdempotency.class);
        verify(idempotencyRepository).save(captor.capture());
        PointIdempotency saved = captor.getValue();

        assertThat(transactionId).isEqualTo(10L);
        assertThat(saved.getStatus()).isEqualTo(IdempotencyStatus.PROCESSING);
        verify(idempotencyRepository)
            .markSuccess(1L, 10L, IdempotencyStatus.SUCCESS, IdempotencyStatus.PROCESSING);
    }

    @Test
    @DisplayName("같은 요청이 다시 들어오면 비즈니스 로직을 실행하지 않고 기존 거래 ID를 반환한다")
    void returnExistingTransactionIdWhenSameRequestIsRepeated() {
        // given
        PointBalance balance = mock(PointBalance.class);
        IdempotentRequest request = request(Map.of("amount", "1000"));
        PointIdempotency existing = successRecord(request, 10L);
        IdempotentOperation operation = mock(IdempotentOperation.class);

        when(balanceRepository.findByAccountIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(balance));
        when(idempotencyRepository.findByKeyForUpdate(ACCOUNT_ID, TransactionType.EARN, IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(existing));

        // when
        Long transactionId = idempotencyService.executeOrReturn(request, operation);

        // then
        assertThat(transactionId).isEqualTo(10L);
        verify(operation, never()).execute(any());
        verify(idempotencyRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 멱등성 키로 다른 요청 본문이 들어오면 충돌로 거절한다")
    void rejectSameKeyWithDifferentRequestBody() {
        // given
        PointBalance balance = mock(PointBalance.class);
        IdempotentRequest request = request(Map.of("amount", "1000"));
        PointIdempotency existing = PointIdempotency.builder()
            .accountId(ACCOUNT_ID)
            .requestType(TransactionType.EARN)
            .idempotencyKey(IDEMPOTENCY_KEY)
            .requestHash(requestHashGenerator.generate(Map.of("amount", "2000")))
            .transactionId(10L)
            .status(IdempotencyStatus.SUCCESS)
            .build();

        when(balanceRepository.findByAccountIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(balance));
        when(idempotencyRepository.findByKeyForUpdate(ACCOUNT_ID, TransactionType.EARN, IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(existing));

        // when
        assertThatThrownBy(() -> idempotencyService.executeOrReturn(request, lockedBalance -> 20L))
            // then
            .isInstanceOf(BaseException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);

        verify(idempotencyRepository, never()).save(any());
    }

    @Test
    @DisplayName("처리 중인 멱등성 레코드가 있으면 중복 실행하지 않고 처리 중 예외를 반환한다")
    void rejectProcessingRecord() {
        // given
        PointBalance balance = mock(PointBalance.class);
        IdempotentRequest request = request(Map.of("amount", "1000"));
        PointIdempotency existing = PointIdempotency.builder()
            .accountId(ACCOUNT_ID)
            .requestType(TransactionType.EARN)
            .idempotencyKey(IDEMPOTENCY_KEY)
            .requestHash(requestHashGenerator.generate(request.body()))
            .status(IdempotencyStatus.PROCESSING)
            .build();

        when(balanceRepository.findByAccountIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(balance));
        when(idempotencyRepository.findByKeyForUpdate(ACCOUNT_ID, TransactionType.EARN, IDEMPOTENCY_KEY))
            .thenReturn(Optional.of(existing));

        // when
        assertThatThrownBy(() -> idempotencyService.executeOrReturn(request, lockedBalance -> 20L))
            // then
            .isInstanceOf(BaseException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.IDEMPOTENCY_PROCESSING);
    }

    @Test
    @DisplayName("멱등성 키가 공백이면 요청을 거절한다")
    void rejectBlankIdempotencyKey() {
        // given & when
        assertThatThrownBy(() -> new IdempotentRequest(
            ACCOUNT_ID,
            TransactionType.EARN,
            " ",
            Map.of("amount", "1000")
        ))
            // then
            .isInstanceOf(BaseException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
    }

    private IdempotentRequest request(Object body) {
        return new IdempotentRequest(ACCOUNT_ID, TransactionType.EARN, IDEMPOTENCY_KEY, body);
    }

    private PointIdempotency successRecord(IdempotentRequest request, Long transactionId) {
        PointIdempotency record = PointIdempotency.builder()
            .accountId(request.accountId())
            .requestType(request.requestType())
            .idempotencyKey(request.idempotencyKey())
            .requestHash(requestHashGenerator.generate(request.body()))
            .transactionId(transactionId)
            .status(IdempotencyStatus.SUCCESS)
            .build();
        return record;
    }
}
