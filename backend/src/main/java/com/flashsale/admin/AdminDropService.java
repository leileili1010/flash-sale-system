package com.flashsale.admin;

import com.flashsale.catalog.CatalogCache;
import com.flashsale.catalog.Drop;
import com.flashsale.catalog.DropRepository;
import com.flashsale.catalog.DropStatus;
import com.flashsale.catalog.Product;
import com.flashsale.catalog.ProductRepository;
import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.inventory.InventoryPreheater;
import com.flashsale.inventory.InventoryService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Drop lifecycle for operators: create (as a draft or published), publish, and cancel before start.
 *
 * <p>Redis and the catalog cache are updated only after the database transaction commits, so a
 * failed change can never leave a drop purchasable in Redis while it is absent from the database.
 */
@Service
public class AdminDropService {

    /** A start time slightly in the past is accepted so operators can launch "now". */
    static final Duration START_GRACE = Duration.ofMinutes(1);

    private final DropRepository drops;
    private final ProductRepository products;
    private final InventoryPreheater preheater;
    private final InventoryService inventory;
    private final CatalogCache catalogCache;
    private final Clock clock;

    AdminDropService(DropRepository drops, ProductRepository products, InventoryPreheater preheater,
                     InventoryService inventory, CatalogCache catalogCache, Clock clock) {
        this.drops = drops;
        this.products = products;
        this.preheater = preheater;
        this.inventory = inventory;
        this.catalogCache = catalogCache;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AdminDropView> list() {
        Instant now = clock.instant();
        return drops.findAllForAdmin().stream().map(drop -> AdminDropView.from(drop, now)).toList();
    }

    @Transactional
    public AdminDropView create(CreateDropRequest request) {
        Instant now = clock.instant();
        if (request.startTime().isBefore(now.minus(START_GRACE))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The start time must not be in the past.");
        }
        Product product = products.findById(request.productId())
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "The selected product does not exist."));

        Drop drop = drops.save(new Drop(product, request.title().strip(), request.dropPrice(), product.getRetailPrice(),
                request.publish() ? DropStatus.PUBLISHED : DropStatus.DRAFT,
                request.startTime(), request.endTime(), request.totalStock(), now));
        if (request.publish()) {
            afterCommit(preheater::preheat);
        }
        return AdminDropView.from(drop, now);
    }

    @Transactional
    public AdminDropView publish(long dropId) {
        Instant now = clock.instant();
        Drop drop = find(dropId);
        try {
            drop.publish(now);
        } catch (IllegalStateException ex) {
            throw new ApiException(ErrorCode.DROP_NOT_EDITABLE, "Only a draft that has not ended can be published.");
        }
        afterCommit(() -> {
            catalogCache.evictDrop(dropId);
            preheater.preheat();
        });
        return AdminDropView.from(drop, now);
    }

    @Transactional
    public AdminDropView cancel(long dropId) {
        Instant now = clock.instant();
        Drop drop = find(dropId);
        try {
            drop.cancel(now);
        } catch (IllegalStateException ex) {
            throw new ApiException(ErrorCode.DROP_NOT_EDITABLE,
                    "Only a drop that has not started can be cancelled; live and ended drops already have orders.");
        }
        afterCommit(() -> {
            inventory.unload(dropId);
            catalogCache.evictDrop(dropId);
        });
        return AdminDropView.from(drop, now);
    }

    private Drop find(long dropId) {
        return drops.findById(dropId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Drop " + dropId + " was not found."));
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
