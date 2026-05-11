package musinsa.pay.point_server.application.earn;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import musinsa.pay.point_server.application.earn.dto.EarnCancelRequest;
import musinsa.pay.point_server.application.earn.dto.EarnCancelResponse;
import musinsa.pay.point_server.application.idempotency.IdempotentOperation;
import musinsa.pay.point_server.application.idempotency.IdempotentRequest;
import musinsa.pay.point_server.application.point.PointCommandTemplate;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 포인트 적립 취소 서비스
 * - 적립 취소 요청 처리
 * - idempotencyKey 기반 중복 요청 방지
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EarnCancelService {

    private final PointCommandTemplate pointCommandTemplate;
    private final EarnCancelProcessor earnCancelProcessor;

    /**
     * 포인트 적립취소
     */
    @Transactional
    public EarnCancelResponse cancel(
        String idempotencyKey,
        EarnCancelRequest request
    ) {
        validateRequest(request);

        IdempotentRequest idempotentRequest = toIdempotentRequest(idempotencyKey, request);
        IdempotentOperation cancelOperation = balance -> earnCancelProcessor.cancel(request, balance);

        Long transactionId = pointCommandTemplate.execute(idempotentRequest, cancelOperation);
        EarnCancelResponse response = earnCancelProcessor.assemble(transactionId);
        log.info("[EarnCancel] 완료: accountId={}, earnPointKey={}, cancelAmount={}, pointKey={}, txId={}",
            response.accountId(), response.earnPointKey(), response.cancelAmount(), response.pointKey(), response.transactionId());
        return response;
    }

    private void validateRequest(EarnCancelRequest request) {
        if (request == null || request.accountId() == null || !StringUtils.hasText(request.earnPointKey())) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
    }

    private IdempotentRequest toIdempotentRequest(
        String idempotencyKey,
        EarnCancelRequest request
    ) {
        return new IdempotentRequest(
            request.accountId(),
            TransactionType.EARN_CANCEL,
            idempotencyKey,
            request
        );
    }
}
