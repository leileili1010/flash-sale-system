package com.flashsale.inventory;

import com.flashsale.catalog.StockReader;
import java.util.OptionalInt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Serves stock from the live Redis counter, which reflects reservations immediately, and
 * falls back to the database for drops that are not loaded (for example, ended drops).
 */
@Primary
@Component
class RedisStockReader implements StockReader {

    private final InventoryService inventory;
    private final StockReader database;

    RedisStockReader(InventoryService inventory, @Qualifier("databaseStockReader") StockReader database) {
        this.inventory = inventory;
        this.database = database;
    }

    @Override
    public OptionalInt availableStock(long dropId) {
        OptionalInt live = inventory.availableStock(dropId);
        return live.isPresent() ? live : database.availableStock(dropId);
    }
}
