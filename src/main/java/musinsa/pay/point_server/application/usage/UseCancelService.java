package musinsa.pay.point_server.application.usage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import musinsa.pay.point_server.application.idempotency.IdempotentOperation;
import musinsa.pay.point_server.application.idempotency.IdempotentRequest;
import musinsa.pay.point_server.application.point.PointCommandTemplate;
import musinsa.pay.point_server.application.usage.dto.UseCancelRequest;
import musinsa.pay.point_server.application.usage.dto.UseCancelResponse;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 포인트 사용 취소 서비스
 * - 요청 검증
 * - 멱등성 처리 및 사용 취소 트랜잭션 생성
 * - 사용 취소 응답 조립
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UseCancelService {

    private final PointCommandTemplate pointCommandTemplate;
    private final UseCancelProcessor useCancelProcessor;

    /**
     * 포인트 사용취소
     */
    @Transactional
    public UseCancelResponse cancel(
        String idempotencyKey,
        UseCancelRequest request
    ) {
        validateRequest(request);

        IdempotentRequest idempotentRequest = toIdempotentRequest(idempotencyKey, request);
        IdempotentOperation cancelOperation = balance -> useCancelProcessor.cancel(request, balance);

        Long transactionId = pointCommandTemplate.execute(idempotentRequest, cancelOperation);
        UseCancelResponse response = useCancelProcessor.assemble(transactionId);
        log.info("[UseCancel] 완료: accountId={}, usePointKey={}, cancelAmount={}, pointKey={}, txId={}",
            response.accountId(), response.usePointKey(), response.cancelAmount(), response.pointKey(), response.transactionId());
        return response;
    }

    private void validateRequest(UseCancelRequest request) {
        if (request == null || request.accountId() == null || request.cancelAmount() == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        if (!StringUtils.hasText(request.usePointKey())) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
    }

    private IdempotentRequest toIdempotentRequest(
        String idempotencyKey,
        UseCancelRequest request
    ) {
        return new IdempotentRequest(
            request.accountId(),
            TransactionType.USE_CANCEL,
            idempotencyKey,
            request
        );
    }
}
