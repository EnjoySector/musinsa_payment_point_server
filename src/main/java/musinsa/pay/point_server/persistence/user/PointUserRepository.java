package musinsa.pay.point_server.persistence.user;

import musinsa.pay.point_server.domain.user.PointUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointUserRepository extends JpaRepository<PointUser, Long> {

}
