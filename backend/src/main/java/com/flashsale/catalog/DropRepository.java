package com.flashsale.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DropRepository extends JpaRepository<Drop, Long> {

    @Query("""
            select d from Drop d join fetch d.product
            where d.id = :id and d.status = com.flashsale.catalog.DropStatus.PUBLISHED
            """)
    Optional<Drop> findPublishedById(long id);

    @Query("""
            select d from Drop d join fetch d.product
            where d.status = com.flashsale.catalog.DropStatus.PUBLISHED
              and d.startTime <= :now and d.endTime > :now
            order by d.endTime
            """)
    List<Drop> findLive(Instant now);

    @Query("""
            select d from Drop d join fetch d.product
            where d.status = com.flashsale.catalog.DropStatus.PUBLISHED and d.startTime > :now
            order by d.startTime
            """)
    List<Drop> findUpcoming(Instant now);

    @Query("""
            select d from Drop d join fetch d.product
            where d.status = com.flashsale.catalog.DropStatus.PUBLISHED and d.endTime <= :now
            order by d.endTime desc
            """)
    List<Drop> findEnded(Instant now, Limit limit);

    @Query("""
            select count(d) > 0 from Drop d
            where d.status = com.flashsale.catalog.DropStatus.PUBLISHED and d.endTime > :now
            """)
    boolean existsLiveOrUpcoming(Instant now);

    boolean existsByProduct_Id(long productId);

    boolean existsByProduct_IdAndStatusAndEndTimeAfter(long productId, DropStatus status, Instant time);
}
