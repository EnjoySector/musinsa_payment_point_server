package musinsa.pay.point_server.application.earn;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import musinsa.pay.point_server.application.earn.dto.EarnPointRequest;
import musinsa.pay.point_server.application.earn.dto.EarnPointResponse;
import musinsa.pay.point_server.application.idempotency.IdempotentOperation;
import musinsa.pay.point_server.application.idempotency.IdempotentRequest;
import musinsa.pay.point_server.application.point.PointCommandTemplate;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.transaction.CreatedByType;
import musinsa.pay.point_server.domain.transaction.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 포인트 적립 서비스
 * - 적립 요청 검증
 * - 멱등성 처리 및 트랜잭션 관리
 * - 적립 처리 로직 위임
 * - 결과 조립 및 반환
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EarnPointService {

    private final PointCommandTemplate pointCommandTemplate;
    private final EarnPointProcessor earnPointProcessor;

    /**
     * 포인트 적립.
     */
    @Transactional
    public EarnPointResponse earn(
        String idempotencyKey,
        EarnPointRequest request
    ) {
        validateRequest(request);

        return earn(idempotencyKey, EarnPointCommand.user(request));
    }

    @Transactional
    public EarnPointResponse earn(
        String idempotencyKey,
        EarnPointCommand command
    ) {
        validateCommand(command);

        IdempotentRequest idempotentRequest = toIdempotentRequest(idempotencyKey, command);
        IdempotentOperation earnOperation = balance -> earnPointProcessor.earn(command, balance);

        Long transactionId = pointCommandTemplate.execute(idempotentRequest, earnOperation);
        EarnPointResponse response = earnPointProcessor.assemble(transactionId);
        log.info("[Earn] 완료: accountId={}, amount={}, earnType={}, pointKey={}, txId={}",
            response.accountId(), response.amount(), response.earnType(), response.pointKey(), response.transactionId());
        return response;
    }

    private void validateRequest(EarnPointRequest request) {
        if (request == null || request.accountId() == null || request.amount() == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
    }

    private void validateCommand(EarnPointCommand command) {
        if (command == null || command.accountId() == null || command.amount() == null || command.createdByType() == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        if (command.createdByType() == CreatedByType.ADMIN && !StringUtils.hasText(command.createdById())) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
    }

    private IdempotentRequest toIdempotentRequest(
        String idempotencyKey,
        EarnPointCommand command
    ) {
        return new IdempotentRequest(
            command.accountId(),
            TransactionType.EARN,
            idempotencyKey,
            command
        );
    }
}
