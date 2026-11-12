package com.flashsale.catalog;

import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-through Redis cache for catalog data that every shopper requests during a drop.
 * Misses are not cached, so unknown ids always fall through to the database.
 */
@Component
public class CatalogCache {

    public static final String PRODUCTS = "products";
    public static final String DROP_INFO = "drop-info";

    private final ProductRepository products;
    private final DropRepository drops;

    CatalogCache(ProductRepository products, DropRepository drops) {
        this.products = products;
        this.drops = drops;
    }

    @Cacheable(cacheNames = PRODUCTS, key = "#productId")
    @Transactional(readOnly = true)
    public ProductView product(long productId) {
        return products.findById(productId)
                .map(ProductView::from)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Product " + productId + " was not found."));
    }

    @Cacheable(cacheNames = DROP_INFO, key = "#dropId")
    @Transactional(readOnly = true)
    public DropInfo dropInfo(long dropId) {
        return drops.findPublishedById(dropId)
                .map(DropInfo::from)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Drop " + dropId + " was not found."));
    }
}
