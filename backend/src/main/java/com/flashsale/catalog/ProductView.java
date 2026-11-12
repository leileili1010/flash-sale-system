package com.flashsale.catalog;

import java.math.BigDecimal;

public record ProductView(
        long id,
        String sku,
        String name,
        String series,
        ProductType type,
        String description,
        String imageUrl,
        BigDecimal retailPrice) {

    public static ProductView from(Product product) {
        return new ProductView(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getSeries(),
                product.getType(),
                product.getDescription(),
                product.getImageUrl(),
                product.getRetailPrice());
    }
}
