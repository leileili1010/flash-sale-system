package com.flashsale.admin;

import com.flashsale.catalog.Drop;
import com.flashsale.catalog.DropPhase;
import com.flashsale.catalog.DropStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** A drop with everything an operator needs: status, schedule and the full stock breakdown. */
record AdminDropView(
        long id,
        String title,
        long productId,
        String productName,
        String imageUrl,
        DropStatus status,
        DropPhase phase,
        Instant startTime,
        Instant endTime,
        BigDecimal dropPrice,
        BigDecimal originalPrice,
        int totalStock,
        int availableStock,
        int lockedStock,
        int soldStock) {

    static AdminDropView from(Drop drop, Instant now) {
        return new AdminDropView(
                drop.getId(), drop.getTitle(), drop.getProduct().getId(), drop.getProduct().getName(),
                drop.getProduct().getImageUrl(), drop.getStatus(), drop.phaseAt(now), drop.getStartTime(),
                drop.getEndTime(), drop.getDropPrice(), drop.getOriginalPrice(), drop.getTotalStock(),
                drop.getAvailableStock(), drop.getLockedStock(), drop.getSoldStock());
    }
}

/**
 * @param publish {@code true} to make the drop visible immediately, {@code false} to save a draft
 */
record CreateDropRequest(
        @NotNull Long productId,
        @NotBlank @Size(max = 160) String title,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal dropPrice,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotNull @Min(1) @Max(100_000) Integer totalStock,
        boolean publish) {

    @AssertTrue(message = "must be after the start time")
    boolean isEndTime() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }
}
