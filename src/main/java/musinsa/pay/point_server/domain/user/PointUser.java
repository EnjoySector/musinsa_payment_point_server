package musinsa.pay.point_server.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import musinsa.pay.point_server.domain.common.BaseEntity;

@Entity
@Getter
@Table(name = "point_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;
}