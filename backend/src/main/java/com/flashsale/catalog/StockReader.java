package com.flashsale.catalog;

import java.util.OptionalInt;

/** Source of the current purchasable quantity for a drop. */
public interface StockReader {

    OptionalInt availableStock(long dropId);
}
