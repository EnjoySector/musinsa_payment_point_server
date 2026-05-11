package musinsa.pay.point_server.domain.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Table(name = "point_balance")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PointBalance {

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "balance_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal balanceAmount;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private PointBalance(
        Long accountId,
        BigDecimal balanceAmount
    ) {
        this.accountId = accountId;
        this.balanceAmount = balanceAmount;
    }

}
