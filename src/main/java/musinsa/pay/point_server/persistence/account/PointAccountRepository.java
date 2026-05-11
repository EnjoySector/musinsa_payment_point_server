package musinsa.pay.point_server.persistence.account;

import musinsa.pay.point_server.domain.account.PointAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointAccountRepository extends JpaRepository<PointAccount, Long> {

}
