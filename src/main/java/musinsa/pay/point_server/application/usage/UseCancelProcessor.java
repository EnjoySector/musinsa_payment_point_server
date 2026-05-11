package musinsa.pay.point_server.application.usage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import musinsa.pay.point_server.application.usage.dto.UseCancelAllocationResponse;
import musinsa.pay.point_server.application.usage.dto.UseCancelRequest;
import musinsa.pay.point_server.application.usage.dto.UseCancelResponse;
import musinsa.pay.point_server.common.exception.BaseException;
import musinsa.pay.point_server.common.exception.ErrorCode;
import musinsa.pay.point_server.common.generator.PointKeyGenerator;
import musinsa.pay.point_server.domain.account.PointBalance;
import musinsa.pay.point_server.domain.earn.EarnStatus;
import musinsa.pay.point_server.domain.earn.PointEarn;
import musinsa.pay.point_server.domain.ledger.LedgerType;
import musinsa.pay.point_server.domain.ledger.PointLedger;
import musinsa.pay.point_server.domain.transaction.PointTransaction;
import musinsa.pay.point_server.domain.usage.PointUsageCancel;
import musinsa.pay.point_server.domain.usage.PointUsageCancelAllocation;
import musinsa.pay.point_server.domain.usage.UsageStatus;
import musinsa.pay.point_server.persistence.account.PointBalanceRepository;
import musinsa.pay.point_server.persistence.earn.PointEarnRepository;
import musinsa.pay.point_server.persistence.ledger.PointLedgerRepository;
import musinsa.pay.point_server.persistence.transaction.PointTransactionRepository;
import musinsa.pay.point_server.persistence.usage.PointUsageAllocationRepository;
import musinsa.pay.point_server.persistence.usage.PointUsageCancelAllocationRepository;
import musinsa.pay.point_server.persistence.usage.PointUsageCancelRepository;
import musinsa.pay.point_server.persistence.usage.PointUsageRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 사용 취소 프로세서
 * - 사용 취소 트랜잭션 생성
 * - 사용 취소 내역 생성
 * - 사용 내역 상태 업데이트
 * - 계정 잔액 업데이트
 * - 원장 기록
 * - 취소 응답 조립
 */
@Component
@RequiredArgsConstructor
public class UseCancelProcessor {

    private static final List<LedgerType> USE_CANCEL_LEDGER_TYPES = List.of(
        LedgerType.USE_CANCEL_ORIGINAL_INCREASE,
        LedgerType.USE_CANCEL_NEW_EARN_INCREASE
    );

    private final UseCancelContextFactory contextFactory;
    private final UseCancelValidator validator;
    private final PointUsageCancelAllocationCalculator allocationCalculator;
    private final PointKeyGenerator pointKeyGenerator;
    private final PointTransactionRepository transactionRepository;
    private final PointUsageCancelRepository usageCancelRepository;
    private final PointUsageCancelAllocationRepository cancelAllocationRepository;
    private final PointUsageRepository usageRepository;
    private final PointUsageAllocationRepository allocationRepository;
    private final PointEarnRepository earnRepository;
    private final PointBalanceRepository balanceRepository;
    private final PointLedgerRepository ledgerRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long cancel(
        UseCancelRequest request,
        PointBalance balance
    ) {
        UseCancelContext context = contextFactory.create(request, balance);
        validator.validate(context);

        List<PointUsageCancelAllocationCandidate> candidates = allocationCalculator.calculate(context);
        PointUsageCancelCreateResult result = createUseCancel(context, candidates);
        recordLedger(context, result);
        return result.transactionId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UseCancelResponse assemble(Long transactionId) {
        PointTransaction transaction = getTransaction(transactionId);
        PointUsageCancel usageCancel = getUsageCancel(transactionId);
        List<PointUsageCancelAllocation> allocations = getAllocations(usageCancel.getId());
        List<PointLedger> ledgers = getLedgers(usageCancel.getId());
        PointTransaction useTransaction = getTransaction(transaction.getRelatedTransactionId());
        return toResponse(transaction, usageCancel, allocations, ledgers, useTransaction);
    }

    private PointUsageCancelCreateResult createUseCancel(
        UseCancelContext context,
        List<PointUsageCancelAllocationCandidate> candidates
    ) {
        PointTransaction transaction = createTransaction(context);
        PointUsageCancel usageCancel = createUsageCancel(context, transaction.getId());
        List<PointUsageCancelAllocationResult> allocations = createAllocations(
            context,
            transaction,
            usageCancel,
            candidates
        );
        updateUsage(context);
        updateBalance(context);
        return PointUsageCancelCreateResult.from(transaction, usageCancel, allocations);
    }

    private PointTransaction createTransaction(UseCancelContext context) {
        String pointKey = pointKeyGenerator.generate();
        return transactionRepository.save(context.toTransaction(pointKey));
    }

    private PointUsageCancel createUsageCancel(
        UseCancelContext context,
        Long transactionId
    ) {
        return usageCancelRepository.save(context.toUsageCancel(transactionId));
    }

    private List<PointUsageCancelAllocationResult> createAllocations(
        UseCancelContext context,
        PointTransaction transaction,
        PointUsageCancel usageCancel,
        List<PointUsageCancelAllocationCandidate> candidates
    ) {
        List<PointUsageCancelAllocationResult> results = new ArrayList<>();
        for (PointUsageCancelAllocationCandidate candidate : candidates) {
            results.add(createAllocation(context, transaction, usageCancel, candidate));
        }
        return results;
    }

    private PointUsageCancelAllocationResult createAllocation(
        UseCancelContext context,
        PointTransaction transaction,
        PointUsageCancel usageCancel,
        PointUsageCancelAllocationCandidate candidate
    ) {
        RestoreResult restoreResult = restoreEarn(context, transaction.getId(), usageCancel.getId(), candidate);
        updateUsageAllocation(candidate);
        PointUsageCancelAllocation cancelAllocation = createCancelAllocation(
            context,
            usageCancel.getId(),
            candidate,
            restoreResult.restoredEarnId()
        );
        return toResult(cancelAllocation, candidate, restoreResult);
    }

    private RestoreResult restoreEarn(
        UseCancelContext context,
        Long useCancelTransactionId,
        Long usageCancelId,
        PointUsageCancelAllocationCandidate candidate
    ) {
        if (candidate.requiresNewEarn()) {
            return createNewEarn(context, useCancelTransactionId, usageCancelId, candidate);
        }
        updateOriginalEarn(context, candidate);
        return new RestoreResult(candidate.originalEarn().getId(), useCancelTransactionId);
    }

    private RestoreResult createNewEarn(
        UseCancelContext context,
        Long useCancelTransactionId,
        Long usageCancelId,
        PointUsageCancelAllocationCandidate candidate
    ) {
        PointTransaction transaction = createReEarnTransaction(context, useCancelTransactionId, candidate.cancelAmount());
        PointEarn earn = earnRepository.save(context.toReEarn(
            transaction.getId(),
            usageCancelId,
            candidate.originalEarn().getId(),
            candidate.cancelAmount()
        ));
        return new RestoreResult(earn.getId(), transaction.getId());
    }

    private PointTransaction createReEarnTransaction(
        UseCancelContext context,
        Long useCancelTransactionId,
        BigDecimal amount
    ) {
        String pointKey = pointKeyGenerator.generate();
        return transactionRepository.save(context.toReEarnTransaction(pointKey, useCancelTransactionId, amount));
    }

    private void updateOriginalEarn(
        UseCancelContext context,
        PointUsageCancelAllocationCandidate candidate
    ) {
        int updatedRows = earnRepository.restoreEarn(
            candidate.originalEarn().getId(),
            context.account().getId(),
            candidate.originalEarnAvailableAfter(),
            candidate.originalEarnConsumedAfter(),
            EarnStatus.AVAILABLE
        );
        if (updatedRows != 1) {
            throw new BaseException(ErrorCode.POINT_USAGE_CANCEL_NOT_ALLOWED);
        }
    }

    private void updateUsageAllocation(PointUsageCancelAllocationCandidate candidate) {
        int updatedRows = allocationRepository.updateCancelState(
            candidate.usageAllocation().getId(),
            candidate.allocationCancelledAfter(),
            candidate.allocationStatusAfter()
        );
        if (updatedRows != 1) {
            throw new BaseException(ErrorCode.POINT_USAGE_CANCEL_NOT_ALLOWED);
        }
    }

    private PointUsageCancelAllocation createCancelAllocation(
        UseCancelContext context,
        Long usageCancelId,
        PointUsageCancelAllocationCandidate candidate,
        Long restoredEarnId
    ) {
        return cancelAllocationRepository.save(context.toCancelAllocation(usageCancelId, candidate, restoredEarnId));
    }

    private PointUsageCancelAllocationResult toResult(
        PointUsageCancelAllocation cancelAllocation,
        PointUsageCancelAllocationCandidate candidate,
        RestoreResult restoreResult
    ) {
        return new PointUsageCancelAllocationResult(
            cancelAllocation.getId(),
            restoreResult.ledgerTransactionId(),
            candidate.usageAllocation().getId(),
            candidate.originalEarn().getId(),
            restoreResult.restoredEarnId(),
            candidate.cancelAmount(),
            candidate.restoreType(),
            candidate.originalEarnAvailableBefore(),
            candidate.originalEarnAvailableAfter()
        );
    }

    private void updateUsage(UseCancelContext context) {
        BigDecimal cancelledAfter = context.usage().getCancelledAmount().add(context.cancelAmount());
        int updatedRows = usageRepository.updateCancelState(
            context.usage().getId(),
            context.account().getId(),
            cancelledAfter,
            usageStatusAfter(context, cancelledAfter)
        );
        if (updatedRows != 1) {
            throw new BaseException(ErrorCode.POINT_USAGE_CANCEL_NOT_ALLOWED);
        }
    }

    private UsageStatus usageStatusAfter(
        UseCancelContext context,
        BigDecimal cancelledAfter
    ) {
        return cancelledAfter.compareTo(context.usage().getUsageAmount()) == 0
            ? UsageStatus.CANCELED
            : UsageStatus.PARTIAL_CANCELED;
    }

    private void updateBalance(UseCancelContext context) {
        int updatedRows = balanceRepository.updateBalanceAmount(
            context.account().getId(),
            context.balanceAfter()
        );
        if (updatedRows != 1) {
            throw new BaseException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    private void recordLedger(
        UseCancelContext context,
        PointUsageCancelCreateResult result
    ) {
        BigDecimal balanceBefore = context.balanceBefore();
        for (PointUsageCancelAllocationResult allocation : result.allocations()) {
            BigDecimal balanceAfter = balanceBefore.add(allocation.cancelAmount());
            ledgerRepository.save(context.toLedger(
                allocation.ledgerTransactionId(),
                result.usageCancelId(),
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

    private PointUsageCancel getUsageCancel(Long transactionId) {
        return usageCancelRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new BaseException(ErrorCode.POINT_USAGE_CANCEL_NOT_FOUND));
    }

    private List<PointUsageCancelAllocation> getAllocations(Long usageCancelId) {
        return cancelAllocationRepository.findByUsageCancelIdOrderByIdAsc(usageCancelId);
    }

    private List<PointLedger> getLedgers(Long usageCancelId) {
        List<PointLedger> ledgers = ledgerRepository
            .findByUsageCancelIdAndLedgerTypeInOrderByIdAsc(
                usageCancelId,
                USE_CANCEL_LEDGER_TYPES
            );
        if (ledgers.isEmpty()) {
            throw new BaseException(ErrorCode.POINT_LEDGER_NOT_FOUND);
        }
        return ledgers;
    }

    private UseCancelResponse toResponse(
        PointTransaction transaction,
        PointUsageCancel usageCancel,
        List<PointUsageCancelAllocation> allocations,
        List<PointLedger> ledgers,
        PointTransaction useTransaction
    ) {
        return new UseCancelResponse(
            transaction.getPointKey(),
            transaction.getId(),
            usageCancel.getId(),
            transaction.getAccountId(),
            useTransaction.getPointKey(),
            usageCancel.getUsageId(),
            usageCancel.getCancelAmount(),
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

    private List<UseCancelAllocationResponse> toAllocationResponses(
        List<PointUsageCancelAllocation> allocations
    ) {
        return allocations.stream()
            .map(this::toAllocationResponse)
            .toList();
    }

    private UseCancelAllocationResponse toAllocationResponse(PointUsageCancelAllocation allocation) {
        return new UseCancelAllocationResponse(
            allocation.getId(),
            allocation.getUsageAllocationId(),
            allocation.getOriginalEarnId(),
            allocation.getRestoredEarnId(),
            allocation.getCancelAmount(),
            allocation.getRestoreType()
        );
    }

    private record RestoreResult(
        Long restoredEarnId,
        Long ledgerTransactionId
    ) {
    }
}
