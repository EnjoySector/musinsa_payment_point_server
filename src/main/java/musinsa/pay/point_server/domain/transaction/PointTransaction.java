package musinsa.pay.point_server.domain.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import musinsa.pay.point_server.domain.common.BaseCreatedEntity;


import java.math.BigDecimal;

@Entity
@Getter
@Table(name = "point_transaction")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTransaction extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "point_key", nullable = false, unique = true, length = 64)
    private String pointKey;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "point_policy_id")
    private Long pointPolicyId;

    @Column(name = "point_user_policy_id")
    private Long pointUserPolicyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal amount;

    @Column(name = "order_no", length = 100)
    private String orderNo;

    @Column(name = "related_transaction_id")
    private Long relatedTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by_type", nullable = false, length = 20)
    private CreatedByType createdByType;

    @Column(name = "created_by_id", length = 64)
    private String createdById;

    @Column(name = "reason", length = 500)
    private String reason;

    @Builder
    private PointTransaction(
        String pointKey,
        Long accountId,
        Long pointPolicyId,
        Long pointUserPolicyId,
        TransactionType transactionType,
        BigDecimal amount,
        String orderNo,
        Long relatedTransactionId,
        CreatedByType createdByType,
        String createdById,
        String reason
    ) {
        this.pointKey = pointKey;
        this.accountId = accountId;
        this.pointPolicyId = pointPolicyId;
        this.pointUserPolicyId = pointUserPolicyId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.orderNo = orderNo;
        this.relatedTransactionId = relatedTransactionId;
        this.createdByType = createdByType;
        this.createdById = createdById;
        this.reason = reason;
    }
}

