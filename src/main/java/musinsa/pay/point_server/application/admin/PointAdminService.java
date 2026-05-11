package musinsa.pay.point_server.application.admin;

import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.application.admin.dto.ManualEarnRequest;
import musinsa.pay.point_server.application.admin.dto.PointPolicyResponse;
import musinsa.pay.point_server.application.admin.dto.PointUserPolicyResponse;
import musinsa.pay.point_server.application.admin.dto.UpdatePointPolicyRequest;
import musinsa.pay.point_server.application.admin.dto.UpdatePointUserPolicyRequest;
import musinsa.pay.point_server.application.earn.EarnPointCommand;
import musinsa.pay.point_server.application.earn.EarnPointService;
import musinsa.pay.point_server.application.earn.dto.EarnPointResponse;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.policy.PointPolicy;
import musinsa.pay.point_server.domain.policy.PointUserPolicy;
import musinsa.pay.point_server.domain.policy.PolicyStatus;
import musinsa.pay.point_server.persistence.policy.PointPolicyRepository;
import musinsa.pay.point_server.persistence.policy.PointUserPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 관리자용 포인트 정책 관리 및 수기 지급 서비스
 */
@Service
@RequiredArgsConstructor
public class PointAdminService {

    private final EarnPointService earnPointService;
    private final PointPolicyRepository pointPolicyRepository;
    private final PointUserPolicyRepository pointUserPolicyRepository;

    @Transactional
    public EarnPointResponse manualEarn(
        Long accountId,
        String idempotencyKey,
        ManualEarnRequest request
    ) {
        validateManualEarn(accountId, request);
        return earnPointService.earn(idempotencyKey, toEarnCommand(accountId, request));
    }

    @Transactional(readOnly = true)
    public List<PointPolicyResponse> getPointPolicies() {
        return pointPolicyRepository.findAllByOrderByIdAsc()
            .stream()
            .map(PointPolicyResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PointUserPolicyResponse> getPointUserPolicies() {
        return pointUserPolicyRepository.findAllByOrderByIdAsc()
            .stream()
            .map(PointUserPolicyResponse::from)
            .toList();
    }

    @Transactional
    public PointPolicyResponse updatePointPolicy(
        Long policyId,
        UpdatePointPolicyRequest request
    ) {
        validatePolicyRequest(policyId, request);
        PointPolicy current = getPointPolicy(policyId);
        PointPolicyUpdate update = PointPolicyUpdate.from(current, request);
        validateExpireDays(update);

        int updatedRows = pointPolicyRepository.updatePolicy(
            policyId,
            update.name(),
            update.maxEarnAmount(),
            update.defaultExpireDays(),
            update.minExpireDays(),
            update.maxExpireDays(),
            update.status(),
            request.adminId()
        );
        if (updatedRows != 1) {
            throw new BaseException(ErrorCode.POINT_POLICY_NOT_FOUND);
        }
        return PointPolicyResponse.from(getPointPolicy(policyId));
    }

    @Transactional
    public PointUserPolicyResponse updatePointUserPolicy(
        Long policyId,
        UpdatePointUserPolicyRequest request
    ) {
        validateUserPolicyRequest(policyId, request);
        PointUserPolicy current = getPointUserPolicy(policyId);
        PointUserPolicyUpdate update = PointUserPolicyUpdate.from(current, request);

        int updatedRows = pointUserPolicyRepository.updatePolicy(
            policyId,
            update.name(),
            update.maxBalanceAmount(),
            update.status(),
            request.adminId()
        );
        if (updatedRows != 1) {
            throw new BaseException(ErrorCode.POINT_USER_POLICY_NOT_FOUND);
        }
        return PointUserPolicyResponse.from(getPointUserPolicy(policyId));
    }

    private EarnPointCommand toEarnCommand(
        Long accountId,
        ManualEarnRequest request
    ) {
        return EarnPointCommand.admin(
            accountId,
            request.amount(),
            request.expireDays(),
            request.adminId(),
            request.reason()
        );
    }

    private void validateManualEarn(
        Long accountId,
        ManualEarnRequest request
    ) {
        if (accountId == null || request == null || request.amount() == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
        if (!StringUtils.hasText(request.adminId())) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
    }

    private void validatePolicyRequest(
        Long policyId,
        UpdatePointPolicyRequest request
    ) {
        if (policyId == null || request == null || !StringUtils.hasText(request.adminId())) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
    }

    private void validateUserPolicyRequest(
        Long policyId,
        UpdatePointUserPolicyRequest request
    ) {
        if (policyId == null || request == null || !StringUtils.hasText(request.adminId())) {
            throw new BaseException(ErrorCode.INVALID_REQUEST);
        }
    }

    private PointPolicy getPointPolicy(Long policyId) {
        return pointPolicyRepository.findById(policyId)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_POLICY_NOT_FOUND));
    }

    private PointUserPolicy getPointUserPolicy(Long policyId) {
        return pointUserPolicyRepository.findById(policyId)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_USER_POLICY_NOT_FOUND));
    }

    private void validateExpireDays(PointPolicyUpdate update) {
        if (update.maxEarnAmount().signum() <= 0) {
            throw new BaseException(ErrorCode.POINT_AMOUNT_INVALID);
        }
        if (update.minExpireDays() < 1 || update.maxExpireDays() <= update.minExpireDays()) {
            throw new BaseException(ErrorCode.POINT_EXPIRE_DAYS_INVALID);
        }
        if (update.defaultExpireDays() < update.minExpireDays()
            || update.defaultExpireDays() > update.maxExpireDays()) {
            throw new BaseException(ErrorCode.POINT_EXPIRE_DAYS_INVALID);
        }
    }

    private record PointPolicyUpdate(
        String name,
        BigDecimal maxEarnAmount,
        Integer defaultExpireDays,
        Integer minExpireDays,
        Integer maxExpireDays,
        PolicyStatus status
    ) {

        private static PointPolicyUpdate from(
            PointPolicy current,
            UpdatePointPolicyRequest request
        ) {
            return new PointPolicyUpdate(
                textOrDefault(request.name(), current.getName()),
                valueOrDefault(request.maxEarnAmount(), current.getMaxEarnAmount()),
                valueOrDefault(request.defaultExpireDays(), current.getDefaultExpireDays()),
                valueOrDefault(request.minExpireDays(), current.getMinExpireDays()),
                valueOrDefault(request.maxExpireDays(), current.getMaxExpireDays()),
                valueOrDefault(request.status(), current.getStatus())
            );
        }
    }

    private record PointUserPolicyUpdate(
        String name,
        BigDecimal maxBalanceAmount,
        PolicyStatus status
    ) {

        private static PointUserPolicyUpdate from(
            PointUserPolicy current,
            UpdatePointUserPolicyRequest request
        ) {
            BigDecimal maxBalanceAmount = valueOrDefault(
                request.maxBalanceAmount(),
                current.getMaxBalanceAmount()
            );
            if (maxBalanceAmount.signum() <= 0) {
                throw new BaseException(ErrorCode.POINT_AMOUNT_INVALID);
            }
            return new PointUserPolicyUpdate(
                textOrDefault(request.name(), current.getName()),
                maxBalanceAmount,
                valueOrDefault(request.status(), current.getStatus())
            );
        }
    }

    private static String textOrDefault(
        String value,
        String defaultValue
    ) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private static <T> T valueOrDefault(
        T value,
        T defaultValue
    ) {
        return value == null ? defaultValue : value;
    }
}
