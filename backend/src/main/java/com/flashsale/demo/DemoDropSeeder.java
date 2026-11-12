package com.flashsale.demo;

import com.flashsale.catalog.Drop;
import com.flashsale.catalog.DropRepository;
import com.flashsale.catalog.DropStatus;
import com.flashsale.catalog.Product;
import com.flashsale.catalog.ProductRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a demo release schedule relative to the current time so a freshly started
 * environment always has something live, something about to start, and some history.
 *
 * <p>Runs only when no live or upcoming drop exists, so it never piles up duplicates and
 * restarting the application days later produces a fresh schedule.
 */
@Component
@ConditionalOnBooleanProperty("flashsale.demo.seed-drops")
public class DemoDropSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDropSeeder.class);

    private final ProductRepository products;
    private final DropRepository drops;
    private final Clock clock;

    public DemoDropSeeder(ProductRepository products, DropRepository drops, Clock clock) {
        this.products = products;
        this.drops = drops;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfNeeded();
    }

    @Transactional
    public int seedIfNeeded() {
        Instant now = clock.instant();
        if (drops.existsLiveOrUpcoming(now)) {
            log.info("Live or upcoming drops already exist; skipping demo seeding");
            return 0;
        }

        List<Drop> schedule = List.of(
                drop("FF-MOSS-001", "Forest Friends: Mochi Moss Launch", "11.99",
                        now.minus(Duration.ofMinutes(10)), Duration.ofHours(4), 100, now),
                drop("NM-DUMP-002", "Night Market: Dumpling Dot Flash Sale", "12.99",
                        now.minus(Duration.ofMinutes(30)), Duration.ofHours(6), 200, now),
                drop("NM-NOOD-003", "Night Market: Neon Noodle Midnight Drop", "13.99",
                        now.plus(Duration.ofMinutes(2)), Duration.ofHours(2), 50, now),
                drop("FF-JUNI-004", "Secret Edition: Juniper Snooze", "14.99",
                        now.plus(Duration.ofHours(1)), Duration.ofHours(2), 10, now),
                drop("FF-MOSS-400", "Mochi Moss 400% Grand Edition", "119.00",
                        now.plus(Duration.ofDays(1)), Duration.ofHours(1), 20, now),
                drop("SB-CORA-001", "Sea Breeze: Coral Kip Plush Preview", "24.99",
                        now.minus(Duration.ofDays(2)), Duration.ofHours(3), 30, now));

        drops.saveAll(schedule);
        log.info("Seeded {} demo drops", schedule.size());
        return schedule.size();
    }

    private Drop drop(String sku, String title, String dropPrice, Instant start, Duration length, int stock, Instant now) {
        Product product = products.findBySku(sku)
                .orElseThrow(() -> new IllegalStateException("Seed product " + sku + " is missing"));
        return new Drop(product, title, new BigDecimal(dropPrice), product.getRetailPrice(), DropStatus.PUBLISHED,
                start, start.plus(length), stock, now);
    }
}
