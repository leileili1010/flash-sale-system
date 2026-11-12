package com.flashsale.catalog;

import java.time.Instant;

/** Where a published drop is in its release window, derived from the current time. */
public enum DropPhase {
    UPCOMING,
    LIVE,
    ENDED;

    public static DropPhase at(Instant now, Instant startTime, Instant endTime) {
        if (now.isBefore(startTime)) {
            return UPCOMING;
        }
        return now.isBefore(endTime) ? LIVE : ENDED;
    }
}
