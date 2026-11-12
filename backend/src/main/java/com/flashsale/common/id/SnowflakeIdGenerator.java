package com.flashsale.common.id;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Generates unique, roughly time-ordered 64-bit identifiers without a database round trip,
 * so order numbers can be assigned at the API edge before the order is persisted.
 *
 * <p>Layout: 1 unused sign bit | 41 bits milliseconds since {@link #EPOCH} | 10 bits worker id |
 * 12 bits per-millisecond sequence. This allows 1,024 instances and 4,096 ids per millisecond
 * per instance for roughly 69 years.
 */
@Component
public class SnowflakeIdGenerator {

    static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    static final int WORKER_BITS = 10;
    static final int SEQUENCE_BITS = 12;
    static final long MAX_WORKER_ID = (1L << WORKER_BITS) - 1;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
    private static final long MAX_BACKWARD_DRIFT_MS = 10;

    private final Clock clock;
    private final long epochMillis = EPOCH.toEpochMilli();
    private final long workerId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(IdProperties properties, Clock clock) {
        if (properties.workerId() < 0 || properties.workerId() > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "flashsale.id.worker-id must be between 0 and " + MAX_WORKER_ID + " but was " + properties.workerId());
        }
        this.workerId = properties.workerId();
        this.clock = clock;
    }

    public synchronized long nextId() {
        long now = clock.millis();

        if (now < lastTimestamp) {
            // Tolerate small NTP corrections by waiting; refuse larger jumps rather than risk duplicates.
            if (lastTimestamp - now > MAX_BACKWARD_DRIFT_MS) {
                throw new IllegalStateException(
                        "Clock moved backwards by " + (lastTimestamp - now) + " ms; refusing to generate ids");
            }
            now = waitUntilAfter(lastTimestamp - 1);
        }

        if (now == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                now = waitUntilAfter(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = now;
        return ((now - epochMillis) << (WORKER_BITS + SEQUENCE_BITS))
                | (workerId << SEQUENCE_BITS)
                | sequence;
    }

    private long waitUntilAfter(long timestamp) {
        long now = clock.millis();
        while (now <= timestamp) {
            Thread.onSpinWait();
            now = clock.millis();
        }
        return now;
    }
}
