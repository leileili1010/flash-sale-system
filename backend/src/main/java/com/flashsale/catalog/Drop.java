package com.flashsale.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A time-boxed, limited-quantity release of one product.
 *
 * <p>Stock columns are deliberately read-only here. Reservations, payments and releases change
 * them through single conditional SQL statements so concurrent updates can never interleave
 * into an inconsistent state; see the inventory and order modules.
 */
@Entity
@Table(name = "drops")
public class Drop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "drop_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal dropPrice;

    @Column(name = "original_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DropStatus status;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "total_stock", nullable = false, updatable = false)
    private int totalStock;

    @Column(name = "available_stock", nullable = false, updatable = false)
    private int availableStock;

    @Column(name = "locked_stock", nullable = false, updatable = false)
    private int lockedStock;

    @Column(name = "sold_stock", nullable = false, updatable = false)
    private int soldStock;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Drop() {
    }

    public Drop(Product product, String title, BigDecimal dropPrice, BigDecimal originalPrice, DropStatus status,
                Instant startTime, Instant endTime, int totalStock, Instant now) {
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        if (totalStock < 0) {
            throw new IllegalArgumentException("totalStock must not be negative");
        }
        this.product = product;
        this.title = title;
        this.dropPrice = dropPrice;
        this.originalPrice = originalPrice;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalStock = totalStock;
        this.availableStock = totalStock;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public DropPhase phaseAt(Instant now) {
        return DropPhase.at(now, startTime, endTime);
    }

    /** Makes a draft visible to shoppers. Only drafts that have not ended can be published. */
    public void publish(Instant now) {
        if (status != DropStatus.DRAFT || !endTime.isAfter(now)) {
            throw new IllegalStateException("Only a draft that has not ended can be published");
        }
        status = DropStatus.PUBLISHED;
        updatedAt = now;
    }

    /** Withdraws a drop before it starts. Live or ended drops already have orders and cannot be cancelled. */
    public void cancel(Instant now) {
        if (status == DropStatus.CANCELLED || !startTime.isAfter(now)) {
            throw new IllegalStateException("Only a drop that has not started can be cancelled");
        }
        status = DropStatus.CANCELLED;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getDropPrice() {
        return dropPrice;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public DropStatus getStatus() {
        return status;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public int getTotalStock() {
        return totalStock;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public int getLockedStock() {
        return lockedStock;
    }

    public int getSoldStock() {
        return soldStock;
    }
}
