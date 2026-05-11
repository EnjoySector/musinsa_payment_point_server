package musinsa.pay.point_server.application.usage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import musinsa.pay.point_server.application.idempotency.IdempotentOperation;
import musinsa.pay.point_server.application.idempotency.IdempotentRequest;
import musinsa.pay.point_server.application.point.PointCommandTemplate;
import musinsa.pay.point_server.application.usage.dto.UsePointRequest;
import musinsa.pay.point_server.application.usage.dto.UsePointResponse;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 포인트 사용 서비스
 * - 포인트 사용 요청 처리
 * - idempotencyKey 기반 중복 요청 방지
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UsePointService {

    private final PointCommandTemplate pointCommandTemplate;
    private final UsePointProcessor usePointProcessor;

    /**
     * 포인트 사용
     */
    @Transactional
    public UsePointResponse use(
        String idempotencyKey,
        UsePointRequest request
    ) {
        validateRequest(request);

        IdempotentRequest idempotentRequest = toIdempotentRequest(idempotencyKey, request);
        IdempotentOperation useOperation = balance -> usePointProcessor.use(request, balance);

        Long transactionId = pointCommandTemplate.execute(idempotentRequest, useOperation);
        UsePointResponse response = usePointProcessor.assemble(transactionId);
        log.info("[Use] 완료: accountId={}, amount={}, orderNo={}, pointKey={}, txId={}",
            response.accountId(), response.amount(), response.orderNo(), response.pointKey(), response.transactionId());
        return response;
    }

    private void validateRequest(UsePointRequest request) {
        if (request == null || request.accountId() == null || request.amount() == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        if (!StringUtils.hasText(request.orderNo())) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
    }

    private IdempotentRequest toIdempotentRequest(
        String idempotencyKey,
        UsePointRequest request
    ) {
        return new IdempotentRequest(
            request.accountId(),
            TransactionType.USE,
            idempotencyKey,
            request
        );
    }
}
