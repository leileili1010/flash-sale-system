package com.flashsale.inventory;

import java.util.List;

/**
 * Redis key layout for a drop's live inventory. The drop id is wrapped in a hash tag
 * ({@code {42}}) so all keys of one drop land in the same Redis Cluster slot, which
 * multi-key Lua scripts require.
 */
final class InventoryKeys {

    private InventoryKeys() {
    }

    static String stock(long dropId) {
        return "drop:{" + dropId + "}:stock";
    }

    static String buyers(long dropId) {
        return "drop:{" + dropId + "}:buyers";
    }

    static String meta(long dropId) {
        return "drop:{" + dropId + "}:meta";
    }

    static List<String> all(long dropId) {
        return List.of(stock(dropId), buyers(dropId), meta(dropId));
    }
}
