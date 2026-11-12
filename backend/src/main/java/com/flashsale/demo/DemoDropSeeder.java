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
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps a demo release schedule, relative to the current time, so a running environment always
 * has drops that are live, about to start, later, and in the past.
 *
 * <p>Each entry is checked per product: an entry is created only when its product has no live
 * or upcoming drop (or, for the historical entry, no drop at all). Restarting the application
 * therefore tops up missing drops without ever duplicating existing ones.
 */
@Component
@ConditionalOnBooleanProperty("flashsale.demo.seed-drops")
public class DemoDropSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDropSeeder.class);

    /** One scheduled drop, with its start expressed relative to "now". */
    record Entry(String sku, String title, String price, Duration startOffset, Duration length, int stock,
                 boolean historical) {

        static Entry upcomingOrLive(String sku, String title, String price, Duration startOffset, Duration length, int stock) {
            return new Entry(sku, title, price, startOffset, length, stock, false);
        }

        static Entry past(String sku, String title, String price, Duration startOffset, Duration length, int stock) {
            return new Entry(sku, title, price, startOffset, length, stock, true);
        }
    }

    static final List<Entry> SCHEDULE = List.of(
            Entry.upcomingOrLive("FF-MOSS-001", "Forest Friends: Mochi Moss Launch", "11.99",
                    Duration.ofMinutes(-10), Duration.ofHours(4), 100),
            Entry.upcomingOrLive("NM-DUMP-002", "Night Market: Dumpling Dot Flash Sale", "12.99",
                    Duration.ofMinutes(-30), Duration.ofHours(6), 200),
            Entry.upcomingOrLive("NM-NOOD-003", "Night Market: Neon Noodle Midnight Drop", "13.99",
                    Duration.ofMinutes(2), Duration.ofHours(2), 50),
            Entry.upcomingOrLive("FF-JUNI-004", "Secret Edition: Juniper Snooze", "14.99",
                    Duration.ofHours(1), Duration.ofHours(2), 10),
            Entry.upcomingOrLive("FF-THIS-002", "Forest Friends: Pip Thistle Release", "12.49",
                    Duration.ofHours(3), Duration.ofHours(3), 60),
            Entry.upcomingOrLive("FF-BRAM-003", "Forest Friends: Bramble Bun Picnic Drop", "12.49",
                    Duration.ofHours(6), Duration.ofHours(3), 80),
            Entry.upcomingOrLive("FF-MOSS-400", "Mochi Moss 400% Grand Edition", "119.00",
                    Duration.ofDays(1), Duration.ofHours(1), 20),
            Entry.past("SB-CORA-001", "Sea Breeze: Coral Kip Plush Preview", "24.99",
                    Duration.ofDays(-2), Duration.ofHours(3), 30));

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

    /** @return the number of drops created */
    @Transactional
    public int seedIfNeeded() {
        Instant now = clock.instant();
        List<Drop> created = new ArrayList<>();
        for (Entry entry : SCHEDULE) {
            Product product = products.findBySku(entry.sku())
                    .orElseThrow(() -> new IllegalStateException("Seed product " + entry.sku() + " is missing"));
            boolean needed = entry.historical()
                    ? !drops.existsByProduct_Id(product.getId())
                    : !drops.existsByProduct_IdAndStatusAndEndTimeAfter(product.getId(), DropStatus.PUBLISHED, now);
            if (needed) {
                Instant start = now.plus(entry.startOffset());
                created.add(new Drop(product, entry.title(), new BigDecimal(entry.price()), product.getRetailPrice(),
                        DropStatus.PUBLISHED, start, start.plus(entry.length()), entry.stock(), now));
            }
        }
        drops.saveAll(created);
        if (created.isEmpty()) {
            log.info("Demo schedule is complete; no drops created");
        } else {
            log.info("Seeded {} demo drops", created.size());
        }
        return created.size();
    }
}
