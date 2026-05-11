package musinsa.pay.point_server.application.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.account.PointBalance;
import musinsa.pay.point_server.domain.idempotency.IdempotencyStatus;
import musinsa.pay.point_server.domain.idempotency.PointIdempotency;
import musinsa.pay.point_server.persistence.account.PointBalanceRepository;
import musinsa.pay.point_server.persistence.idempotency.PointIdempotencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * 멱등성 처리 서비스
 * - 멱등성 키와 요청 본문 해시를 기반으로 중복 요청 검증
 * - 계정 잔액에 대한 비관적 락을 사용하여 직렬화 보장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final PointIdempotencyRepository idempotencyRepository;
    private final PointBalanceRepository balanceRepository;
    private final RequestHashGenerator requestHashGenerator;

    /**
     * 멱등성 처리
     * 계정 잔액 row 비관적 락 기준 직렬화
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long executeOrReturn(
        IdempotentRequest request,
        IdempotentOperation operation
    ) {
        String hash = requestHashGenerator.generate(request.body());
        PointBalance balance = acquireBalanceLock(request.accountId());

        Optional<PointIdempotency> existingRecord = idempotencyRepository.findByKeyForUpdate(
            request.accountId(),
            request.requestType(),
            request.idempotencyKey()
        );

        if (existingRecord.isPresent()) {
            return verifyAndReturnTransactionId(existingRecord.get(), hash);
        }

        return executeNewRequest(request, hash, balance, operation);
    }

    private PointBalance acquireBalanceLock(Long accountId) {
        return balanceRepository.findByAccountIdForUpdate(accountId)
            .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private Long verifyAndReturnTransactionId(
        PointIdempotency record,
        String hash
    ) {
        if (!Objects.equals(record.getRequestHash(), hash)) {
            log.warn("[멱등성] 요청 본문 해시가 일치하지 않습니다. accountId={}, requestType={}, key={}",
                record.getAccountId(), record.getRequestType(), record.getIdempotencyKey());
            throw new BaseException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }

        return switch (record.getStatus()) {
            case SUCCESS -> record.getTransactionId();
            case PROCESSING -> throw new BaseException(ErrorCode.IDEMPOTENCY_PROCESSING);
            case FAILED -> throw new BaseException(ErrorCode.IDEMPOTENCY_CONFLICT);
        };
    }

    private Long executeNewRequest(
        IdempotentRequest request,
        String hash,
        PointBalance balance,
        IdempotentOperation operation
    ) {
        PointIdempotency record = PointIdempotency.builder()
            .accountId(request.accountId())
            .requestType(request.requestType())
            .idempotencyKey(request.idempotencyKey())
            .requestHash(hash)
            .status(IdempotencyStatus.PROCESSING)
            .build();
        idempotencyRepository.save(record);

        Long transactionId = operation.execute(balance);
        markSuccess(record, transactionId);
        return transactionId;
    }

    private void markSuccess(
        PointIdempotency record,
        Long transactionId
    ) {
        if (record.getStatus() != IdempotencyStatus.PROCESSING) {
            throw new IllegalStateException("PROCESSING 상태에서만 SUCCESS로 변경할 수 있습니다. status=" + record.getStatus());
        }
        if (transactionId == null) {
            throw new IllegalArgumentException("transactionId는 null일 수 없습니다.");
        }

        int updatedRows = idempotencyRepository.markSuccess(
            record.getId(),
            transactionId,
            IdempotencyStatus.SUCCESS,
            IdempotencyStatus.PROCESSING
        );

        if (updatedRows != 1) {
            throw new IllegalStateException("멱등성 성공 상태 변경에 실패했습니다. id=" + record.getId());
        }
    }
}
