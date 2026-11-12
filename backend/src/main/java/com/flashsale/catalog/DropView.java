package com.flashsale.catalog;

import java.math.BigDecimal;
import java.time.Instant;

/** A drop as presented to shoppers: static details plus live phase and stock. */
public record DropView(
        long id,
        String title,
        ProductView product,
        BigDecimal dropPrice,
        BigDecimal originalPrice,
        Instant startTime,
        Instant endTime,
        int totalStock,
        int availableStock,
        DropPhase phase,
        boolean soldOut) {

    public static DropView of(DropInfo info, int availableStock, Instant now) {
        return new DropView(
                info.id(),
                info.title(),
                info.product(),
                info.dropPrice(),
                info.originalPrice(),
                info.startTime(),
                info.endTime(),
                info.totalStock(),
                availableStock,
                DropPhase.at(now, info.startTime(), info.endTime()),
                availableStock <= 0);
    }
}
