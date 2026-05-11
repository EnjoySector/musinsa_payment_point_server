package musinsa.pay.point_server.application.usage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.domain.earn.EarnStatus;
import musinsa.pay.point_server.domain.earn.EarnType;
import musinsa.pay.point_server.domain.earn.PointEarn;
import musinsa.pay.point_server.persistence.earn.PointEarnRepository;
import org.springframework.stereotype.Component;

/**
 * 포인트 사용 적립별 차감 배분 계산
 * 수기 지급 우선, 만료일 빠른 순서
 */
@Component
@RequiredArgsConstructor
public class PointUsageAllocationCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PointEarnRepository earnRepository;

    public List<PointUsageAllocationCandidate> calculate(UsePointContext context) {
        List<PointEarn> earns = findUsableEarns(context.account().getId());
        List<PointUsageAllocationCandidate> candidates = allocate(context.amount(), earns);
        validateAllocatedAmount(context.amount(), candidates);
        return candidates;
    }

    private List<PointEarn> findUsableEarns(Long accountId) {
        return earnRepository.findUsableEarnsForUpdate(
            accountId,
            EarnStatus.AVAILABLE,
            ZERO,
            LocalDateTime.now(),
            EarnType.MANUAL
        );
    }

    private List<PointUsageAllocationCandidate> allocate(
        BigDecimal amount,
        List<PointEarn> earns
    ) {
        BigDecimal remainingAmount = amount;
        List<PointUsageAllocationCandidate> candidates = new ArrayList<>();
        for (PointEarn earn : earns) {
            if (remainingAmount.compareTo(ZERO) == 0) {
                break;
            }
            PointUsageAllocationCandidate candidate = toCandidate(earn, candidates.size() + 1, remainingAmount);
            candidates.add(candidate);
            remainingAmount = remainingAmount.subtract(candidate.amount());
        }
        return candidates;
    }

    private PointUsageAllocationCandidate toCandidate(
        PointEarn earn,
        int allocationSeq,
        BigDecimal remainingAmount
    ) {
        BigDecimal amount = earn.getAvailableAmount().min(remainingAmount);
        BigDecimal availableAfter = earn.getAvailableAmount().subtract(amount);
        BigDecimal consumedAfter = earn.getConsumedAmount().add(amount);
        EarnStatus statusAfter = availableAfter.compareTo(ZERO) == 0 ? EarnStatus.EXHAUSTED : EarnStatus.AVAILABLE;
        return new PointUsageAllocationCandidate(
            earn,
            allocationSeq,
            amount,
            earn.getAvailableAmount(),
            availableAfter,
            consumedAfter,
            statusAfter
        );
    }

    private void validateAllocatedAmount(
        BigDecimal amount,
        List<PointUsageAllocationCandidate> candidates
    ) {
        BigDecimal allocatedAmount = candidates.stream()
            .map(PointUsageAllocationCandidate::amount)
            .reduce(ZERO, BigDecimal::add);
        if (allocatedAmount.compareTo(amount) != 0) {
            throw new BaseException(ErrorCode.POINT_BALANCE_NOT_ENOUGH);
        }
    }
}
