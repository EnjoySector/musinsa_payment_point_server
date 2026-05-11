package musinsa.pay.point_server.application.earn;

import java.math.BigDecimal;
import musinsa.pay.point_server.application.earn.dto.EarnPointRequest;
import musinsa.pay.point_server.domain.earn.EarnType;
import musinsa.pay.point_server.domain.transaction.CreatedByType;

/**
 * 적립 처리 내부 커맨드.
 * 외부 요청 DTO에서 받지 않는 행위자 정보를 서비스 내부에서 확정한다.
 */
public record EarnPointCommand(
    Long accountId,
    BigDecimal amount,
    EarnType earnType,
    Integer expireDays,
    CreatedByType createdByType,
    String createdById,
    String reason
) {

    public static EarnPointCommand user(EarnPointRequest request) {
        return new EarnPointCommand(
            request.accountId(),
            request.amount(),
            request.earnType(),
            request.expireDays(),
            CreatedByType.USER,
            null,
            request.reason()
        );
    }

    public static EarnPointCommand admin(
        Long accountId,
        BigDecimal amount,
        Integer expireDays,
        String adminId,
        String reason
    ) {
        return new EarnPointCommand(
            accountId,
            amount,
            EarnType.MANUAL,
            expireDays,
            CreatedByType.ADMIN,
            adminId,
            reason
        );
    }
}
