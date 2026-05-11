package musinsa.pay.point_server.application.usage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.application.usage.dto.UsePointAllocationResponse;
import musinsa.pay.point_server.application.usage.dto.UsePointRequest;
import musinsa.pay.point_server.application.usage.dto.UsePointResponse;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.common.generator.PointKeyGenerator;
import musinsa.pay.point_server.domain.account.PointBalance;
import musinsa.pay.point_server.domain.ledger.LedgerType;
import musinsa.pay.point_server.domain.ledger.PointLedger;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.usage.PointUsage;
import musinsa.pay.point_server.domain.usage.PointUsageAllocation;
import musinsa.pay.point_server.persistence.account.PointBalanceRepository;
import musinsa.pay.point_server.persistence.earn.PointEarnRepository;
import musinsa.pay.point_server.persistence.ledger.PointLedgerRepository;
import musinsa.pay.point_server.persistence.transaction.PointTransactionRepository;
import musinsa.pay.point_server.persistence.usage.PointUsageAllocationRepository;
import musinsa.pay.point_server.persistence.usage.PointUsageRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 사용 흐름을 처리하고 결과 조회 응답까지 조립
 */
@Component
@RequiredArgsConstructor
public class UsePointProcessor {

    private final UsePointContextFactory contextFactory;
    private final UsePointValidator validator;
    private final PointUsageAllocationCalculator allocationCalculator;
    private final PointKeyGenerator pointKeyGenerator;
    private final PointTransactionRepository transactionRepository;
    private final PointUsageRepository usageRepository;
    private final PointUsageAllocationRepository allocationRepository;
    private final PointEarnRepository earnRepository;
    private final PointBalanceRepository balanceRepository;
    private final PointLedgerRepository ledgerRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long use(
        UsePointRequest request,
        PointBalance balance
    ) {
        UsePointContext context = contextFactory.create(request, balance);
        validator.validate(context);

        List<PointUsageAllocationCandidate> candidates = allocationCalculator.calculate(context);
        PointUsageCreateResult result = createUse(context, candidates);
        recordLedger(context, result);
        return result.transactionId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UsePointResponse assemble(Long transactionId) {
        PointTransaction transaction = getTransaction(transactionId);
        PointUsage usage = getUsage(transactionId);
        List<PointUsageAllocation> allocations = getAllocations(usage.getId());
        List<PointLedger> ledgers = getLedgers(transactionId);
        return toResponse(transaction, usage, allocations, ledgers);
    }

    private PointUsageCreateResult createUse(
        UsePointContext context,
        List<PointUsageAllocationCandidate> candidates
    ) {
        PointTransaction transaction = createTransaction(context);
        PointUsage usage = createUsage(context, transaction.getId());
        List<PointUsageAllocationResult> allocations = createAllocations(context, usage.getId(), candidates);
        updateEarns(context, candidates);
        updateBalance(context);
        return PointUsageCreateResult.from(transaction, usage, allocations);
    }

    private PointTransaction createTransaction(UsePointContext context) {
        String pointKey = pointKeyGenerator.generate();
        return transactionRepository.save(context.toTransaction(pointKey));
    }

    private PointUsage createUsage(
        UsePointContext context,
        Long transactionId
    ) {
        return usageRepository.save(context.toUsage(transactionId));
    }

    private List<PointUsageAllocationResult> createAllocations(
        UsePointContext context,
        Long usageId,
        List<PointUsageAllocationCandidate> candidates
    ) {
        List<PointUsageAllocationResult> results = new ArrayList<>();
        for (PointUsageAllocationCandidate candidate : candidates) {
            results.add(createAllocation(context, usageId, candidate));
        }
        return results;
    }

    private PointUsageAllocationResult createAllocation(
        UsePointContext context,
        Long usageId,
        PointUsageAllocationCandidate candidate
    ) {
        PointUsageAllocation allocation = allocationRepository.save(context.toAllocation(usageId, candidate));
        return PointUsageAllocationResult.from(allocation.getId(), candidate);
    }

    private void updateEarns(
        UsePointContext context,
        List<PointUsageAllocationCandidate> candidates
    ) {
        for (PointUsageAllocationCandidate candidate : candidates) {
            updateEarn(context, candidate);
        }
    }

    private void updateEarn(
        UsePointContext context,
        PointUsageAllocationCandidate candidate
    ) {
        int updatedRows = earnRepository.useEarn(
            candidate.earn().getId(),
            context.account().getId(),
            candidate.earnAvailableAfter(),
            candidate.earnConsumedAfter(),
            candidate.earnStatusAfter()
        );
        if (updatedRows != 1) {
            throw new BaseException(ErrorCode.POINT_USAGE_NOT_FOUND);
        }
    }

    private void updateBalance(UsePointContext context) {
        int updatedRows = balanceRepository.updateBalanceAmount(
            context.account().getId(),
            context.balanceAfter()
        );
        if (updatedRows != 1) {
            throw new BaseException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    private void recordLedger(
        UsePointContext context,
        PointUsageCreateResult result
    ) {
        BigDecimal balanceBefore = context.balanceBefore();
        for (PointUsageAllocationResult allocation : result.allocations()) {
            BigDecimal balanceAfter = balanceBefore.subtract(allocation.amount());
            ledgerRepository.save(context.toLedger(
                result.transactionId(),
                result.usageId(),
                allocation,
                balanceBefore,
                balanceAfter
            ));
            balanceBefore = balanceAfter;
        }
    }

    private PointTransaction getTransaction(Long transactionId) {
        return transactionRepository.findById(transactionId)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_TRANSACTION_NOT_FOUND));
    }

    private PointUsage getUsage(Long transactionId) {
        return usageRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_USAGE_NOT_FOUND));
    }

    private List<PointUsageAllocation> getAllocations(Long usageId) {
        return allocationRepository.findByUsageIdOrderByAllocationSeqAsc(usageId);
    }

    private List<PointLedger> getLedgers(Long transactionId) {
        List<PointLedger> ledgers = ledgerRepository
            .findByTransactionIdAndLedgerTypeOrderByIdAsc(
                transactionId,
                LedgerType.USE_DECREASE
            );
        if (ledgers.isEmpty()) {
            throw new BaseException(ErrorCode.POINT_LEDGER_NOT_FOUND);
        }
        return ledgers;
    }

    private UsePointResponse toResponse(
        PointTransaction transaction,
        PointUsage usage,
        List<PointUsageAllocation> allocations,
        List<PointLedger> ledgers
    ) {
        return new UsePointResponse(
            transaction.getPointKey(),
            transaction.getId(),
            usage.getId(),
            transaction.getAccountId(),
            usage.getOrderNo(),
            usage.getUsageAmount(),
            firstBalanceBefore(ledgers),
            lastBalanceAfter(ledgers),
            toAllocationResponses(allocations)
        );
    }

    private BigDecimal firstBalanceBefore(List<PointLedger> ledgers) {
        return ledgers.get(0).getAccountBalanceBefore();
    }

    private BigDecimal lastBalanceAfter(List<PointLedger> ledgers) {
        return ledgers.get(ledgers.size() - 1).getAccountBalanceAfter();
    }

    private List<UsePointAllocationResponse> toAllocationResponses(List<PointUsageAllocation> allocations) {
        return allocations.stream()
            .map(this::toAllocationResponse)
            .toList();
    }

    private UsePointAllocationResponse toAllocationResponse(PointUsageAllocation allocation) {
        return new UsePointAllocationResponse(
            allocation.getId(),
            allocation.getEarnId(),
            allocation.getAllocationSeq(),
            allocation.getAmount()
        );
    }
}
