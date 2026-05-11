package musinsa.pay.point_server.persistence.usage;

import java.util.List;
import musinsa.pay.point_server.domain.usage.PointUsageCancelAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointUsageCancelAllocationRepository extends JpaRepository<PointUsageCancelAllocation, Long> {

    List<PointUsageCancelAllocation> findByUsageCancelIdOrderByIdAsc(
        Long usageCancelId
    );
}
