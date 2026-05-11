package musinsa.pay.point_server.application.usage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.earn.EarnStatus;
import musinsa.pay.point_server.domain.earn.PointEarn;
import musinsa.pay.point_server.domain.usage.PointUsageAllocation;
import musinsa.pay.point_server.domain.usage.RestoreType;
import musinsa.pay.point_server.domain.usage.UsageAllocationStatus;
import musinsa.pay.point_server.persistence.earn.PointEarnRepository;
import musinsa.pay.point_server.persistence.usage.PointUsageAllocationRepository;
import org.springframework.stereotype.Component;

/**
 * 포인트 사용취소 배분 계산
 * 기존 사용 배분 순서 기준 복원
 */
@Component
@RequiredArgsConstructor
public class PointUsageCancelAllocationCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PointUsageAllocationRepository allocationRepository;
    private final PointEarnRepository earnRepository;

    public List<PointUsageCancelAllocationCandidate> calculate(UseCancelContext context) {
        List<PointUsageAllocation> allocations = allocationRepository
            .findCancelableAllocationsForUpdate(context.usage().getId());
        List<PointUsageCancelAllocationCandidate> candidates = allocate(context.cancelAmount(), allocations);
        validateAllocatedAmount(context.cancelAmount(), candidates);
        return candidates;
    }

    private List<PointUsageCancelAllocationCandidate> allocate(
        BigDecimal cancelAmount,
        List<PointUsageAllocation> allocations
    ) {
        BigDecimal remainingAmount = cancelAmount;
        List<PointUsageCancelAllocationCandidate> candidates = new ArrayList<>();
        for (PointUsageAllocation allocation : allocations) {
            if (remainingAmount.compareTo(ZERO) == 0) {
                break;
            }
            PointUsageCancelAllocationCandidate candidate = toCandidate(allocation, remainingAmount);
            candidates.add(candidate);
            remainingAmount = remainingAmount.subtract(candidate.cancelAmount());
        }
        return candidates;
    }

    private PointUsageCancelAllocationCandidate toCandidate(
        PointUsageAllocation allocation,
        BigDecimal remainingAmount
    ) {
        PointEarn earn = getEarn(allocation.getEarnId());
        BigDecimal cancelAmount = cancelableAmount(allocation).min(remainingAmount);
        return createCandidate(allocation, earn, cancelAmount);
    }

    private PointEarn getEarn(Long earnId) {
        return earnRepository.findByIdForUpdate(earnId)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_EARN_NOT_FOUND));
    }

    private BigDecimal cancelableAmount(PointUsageAllocation allocation) {
        return allocation.getAmount().subtract(allocation.getCancelledAmount());
    }

    private PointUsageCancelAllocationCandidate createCandidate(
        PointUsageAllocation allocation,
        PointEarn earn,
        BigDecimal cancelAmount
    ) {
        RestoreType restoreType = restoreType(earn);
        BigDecimal cancelledAfter = allocation.getCancelledAmount().add(cancelAmount);
        return new PointUsageCancelAllocationCandidate(
            allocation,
            earn,
            cancelAmount,
            cancelledAfter,
            allocationStatusAfter(allocation, cancelledAfter),
            restoreType,
            earnAvailableBefore(earn, restoreType),
            earnAvailableAfter(earn, restoreType, cancelAmount),
            earn.getConsumedAmount().subtract(cancelAmount),
            EarnStatus.AVAILABLE
        );
    }

    private RestoreType restoreType(PointEarn earn) {
        if (earn.getStatus() == EarnStatus.CANCELED || earn.getStatus() == EarnStatus.EXPIRED) {
            return RestoreType.NEW_EARN;
        }
        return earn.getExpiresAt().isAfter(LocalDateTime.now())
            ? RestoreType.ORIGINAL_EARN
            : RestoreType.NEW_EARN;
    }

    private UsageAllocationStatus allocationStatusAfter(
        PointUsageAllocation allocation,
        BigDecimal cancelledAfter
    ) {
        return cancelledAfter.compareTo(allocation.getAmount()) == 0
            ? UsageAllocationStatus.CANCELED
            : UsageAllocationStatus.PARTIAL_CANCELED;
    }

    private BigDecimal earnAvailableBefore(
        PointEarn earn,
        RestoreType restoreType
    ) {
        return restoreType == RestoreType.ORIGINAL_EARN ? earn.getAvailableAmount() : ZERO;
    }

    private BigDecimal earnAvailableAfter(
        PointEarn earn,
        RestoreType restoreType,
        BigDecimal cancelAmount
    ) {
        return restoreType == RestoreType.ORIGINAL_EARN
            ? earn.getAvailableAmount().add(cancelAmount)
            : cancelAmount;
    }

    private void validateAllocatedAmount(
        BigDecimal cancelAmount,
        List<PointUsageCancelAllocationCandidate> candidates
    ) {
        BigDecimal allocatedAmount = candidates.stream()
            .map(PointUsageCancelAllocationCandidate::cancelAmount)
            .reduce(ZERO, BigDecimal::add);
        if (allocatedAmount.compareTo(cancelAmount) != 0) {
            throw new BaseException(ErrorCode.POINT_USAGE_CANCEL_ALLOCATION_INSUFFICIENT);
        }
    }
}
