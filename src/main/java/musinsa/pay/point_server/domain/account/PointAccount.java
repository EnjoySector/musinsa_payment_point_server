package musinsa.pay.point_server.domain.account;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import musinsa.pay.point_server.domain.common.BaseEntity;

@Entity
@Getter
@Table(name = "point_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "point_user_policy_id", nullable = false)
    private Long pointUserPolicyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "status_updated_at", nullable = false)
    private LocalDateTime statusUpdatedAt;

    @Column(name = "status_updated_by", nullable = false, length = 64)
    private String statusUpdatedBy;

    @Builder
    private PointAccount(
        Long userId,
        Long pointUserPolicyId,
        AccountStatus status,
        LocalDateTime statusUpdatedAt,
        String statusUpdatedBy
    ) {
        this.userId = userId;
        this.pointUserPolicyId = pointUserPolicyId;
        this.status = status;
        this.statusUpdatedAt = statusUpdatedAt;
        this.statusUpdatedBy = statusUpdatedBy;
    }
}
