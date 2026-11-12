package com.flashsale.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByOrderNo(long orderNo);

    List<PurchaseOrder> findByUserIdOrderByCreatedAtDesc(long userId, Limit limit);
}
