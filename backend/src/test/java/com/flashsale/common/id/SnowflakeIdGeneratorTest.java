package com.flashsale.common.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class SnowflakeIdGeneratorTest {

    private final Clock clock = Clock.systemUTC();

    @Test
    void idsAreUniqueUnderConcurrency() throws Exception {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(new IdProperties(7), clock);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        int threads = 32;
        int perThread = 5_000;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        ids.add(generator.nextId());
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertThat(ids).hasSize(threads * perThread);
    }

    @Test
    void idsIncreaseMonotonicallyOnOneInstance() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(new IdProperties(1), clock);
        long previous = generator.nextId();
        for (int i = 0; i < 50_000; i++) {
            long next = generator.nextId();
            assertThat(next).isGreaterThan(previous);
            previous = next;
        }
    }

    @Test
    void workerIdIsEncodedInTheId() {
        long workerId = 513;
        long id = new SnowflakeIdGenerator(new IdProperties(workerId), clock).nextId();
        long decoded = (id >> SnowflakeIdGenerator.SEQUENCE_BITS) & SnowflakeIdGenerator.MAX_WORKER_ID;
        assertThat(decoded).isEqualTo(workerId);
    }

    @Test
    void rejectsOutOfRangeWorkerId() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(new IdProperties(1024), clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeIdGenerator(new IdProperties(-1), clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesToGenerateWhenClockJumpsBackwardsSignificantly() {
        MutableClock mutable = new MutableClock(Instant.parse("2026-06-01T00:00:00Z"));
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(new IdProperties(1), mutable);
        generator.nextId();

        mutable.instant = mutable.instant.minusSeconds(1);

        assertThatThrownBy(generator::nextId).isInstanceOf(IllegalStateException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
