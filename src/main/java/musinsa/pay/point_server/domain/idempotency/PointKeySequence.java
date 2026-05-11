package musinsa.pay.point_server.domain.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import musinsa.pay.point_server.domain.common.BaseEntity;

@Entity
@Getter
@Table(name = "point_key_sequence")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointKeySequence extends BaseEntity {

    @Id
    @Column(name = "date_key", length = 6, nullable = false, updatable = false)
    private String dateKey;

    @Column(name = "next_value", nullable = false)
    private Long nextValue;

    public PointKeySequence(String dateKey) {
        this.dateKey = dateKey;
        this.nextValue = 1L;
    }

    public Long getAndIncrement() {
        Long current = this.nextValue;
        this.nextValue = current + 1;
        return current;
    }
}
