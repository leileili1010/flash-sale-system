package com.flashsale.catalog;

import java.util.List;
import java.util.OptionalInt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads stock straight from the system of record with a single indexed primary-key lookup. */
@Component
class DatabaseStockReader implements StockReader {

    private final JdbcTemplate jdbc;

    DatabaseStockReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OptionalInt availableStock(long dropId) {
        List<Integer> rows = jdbc.queryForList("SELECT available_stock FROM drops WHERE id = ?", Integer.class, dropId);
        return rows.isEmpty() ? OptionalInt.empty() : OptionalInt.of(rows.getFirst());
    }
}
