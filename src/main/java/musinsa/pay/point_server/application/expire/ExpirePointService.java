package musinsa.pay.point_server.application.expire;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import musinsa.pay.point_server.application.expire.dto.ExpirePointResponse;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.account.PointBalance;
import musinsa.pay.point_server.persistence.account.PointBalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 만료 서비스
 * - 계정 단위 만료 포인트 정리
 * - 만료 내역 기록
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExpirePointService {

    private final PointBalanceRepository balanceRepository;
    private final ExpirePointProcessor expirePointProcessor;

    /**
     * 계정 단위 만료 포인트 정리
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExpirePointResponse expire(Long accountId) {
        PointBalance balance = balanceRepository.findByAccountIdForUpdate(accountId)
            .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_FOUND));
        ExpirePointResult result = expirePointProcessor.expire(balance);
        ExpirePointResponse response = ExpirePointResponse.from(result);
        if (response.expiredAmount().signum() > 0) {
            log.info("[Expire] 완료: accountId={}, expiredCount={}, expiredAmount={}",
                response.accountId(), response.expiredCount(), response.expiredAmount());
        }
        return response;
    }
}
