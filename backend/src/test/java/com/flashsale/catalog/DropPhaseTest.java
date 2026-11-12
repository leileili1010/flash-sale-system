package com.flashsale.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DropPhaseTest {

    private final Instant start = Instant.parse("2026-11-20T18:00:00Z");
    private final Instant end = Instant.parse("2026-11-20T20:00:00Z");

    @Test
    void upcomingBeforeStart() {
        assertThat(DropPhase.at(start.minusMillis(1), start, end)).isEqualTo(DropPhase.UPCOMING);
    }

    @Test
    void liveFromTheExactStartInstant() {
        assertThat(DropPhase.at(start, start, end)).isEqualTo(DropPhase.LIVE);
    }

    @Test
    void endedFromTheExactEndInstant() {
        assertThat(DropPhase.at(end.minusMillis(1), start, end)).isEqualTo(DropPhase.LIVE);
        assertThat(DropPhase.at(end, start, end)).isEqualTo(DropPhase.ENDED);
    }
}
