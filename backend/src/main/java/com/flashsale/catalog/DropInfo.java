package com.flashsale.catalog;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The parts of a drop that rarely change and are safe to cache. Stock is excluded on purpose:
 * it changes on every purchase and is always read live.
 */
public record DropInfo(
        long id,
        String title,
        ProductView product,
        BigDecimal dropPrice,
        BigDecimal originalPrice,
        Instant startTime,
        Instant endTime,
        int totalStock) {

    public static DropInfo from(Drop drop) {
        return new DropInfo(
                drop.getId(),
                drop.getTitle(),
                ProductView.from(drop.getProduct()),
                drop.getDropPrice(),
                drop.getOriginalPrice(),
                drop.getStartTime(),
                drop.getEndTime(),
                drop.getTotalStock());
    }
}
