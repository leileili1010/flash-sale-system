package com.flashsale.catalog;

import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    static final int ENDED_DROPS_LIMIT = 20;

    private final ProductRepository products;
    private final DropRepository drops;
    private final CatalogCache cache;
    private final StockReader stockReader;
    private final Clock clock;

    CatalogService(ProductRepository products, DropRepository drops, CatalogCache cache,
                   StockReader stockReader, Clock clock) {
        this.products = products;
        this.drops = drops;
        this.cache = cache;
        this.stockReader = stockReader;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProductView> listProducts() {
        return products.findAll(Sort.by("series", "id")).stream().map(ProductView::from).toList();
    }

    public ProductView getProduct(long productId) {
        return cache.product(productId);
    }

    /**
     * Lists published drops, optionally filtered by phase. Without a filter the result is
     * ordered live first, then upcoming, then recently ended.
     */
    @Transactional(readOnly = true)
    public List<DropView> listDrops(@Nullable DropPhase phase) {
        Instant now = clock.instant();
        Stream<Drop> result = switch (phase) {
            case LIVE -> drops.findLive(now).stream();
            case UPCOMING -> drops.findUpcoming(now).stream();
            case ENDED -> drops.findEnded(now, Limit.of(ENDED_DROPS_LIMIT)).stream();
            case null -> Stream.of(
                            drops.findLive(now),
                            drops.findUpcoming(now),
                            drops.findEnded(now, Limit.of(ENDED_DROPS_LIMIT)))
                    .flatMap(List::stream);
        };
        return result.map(drop -> DropView.of(DropInfo.from(drop), drop.getAvailableStock(), now)).toList();
    }

    /** Hot path while a drop is live: cached static details plus one live stock lookup. */
    public DropView getDrop(long dropId) {
        DropInfo info = cache.dropInfo(dropId);
        int available = stockReader.availableStock(dropId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Drop " + dropId + " was not found."));
        return DropView.of(info, available, clock.instant());
    }
}
