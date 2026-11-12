package com.flashsale.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.flashsale.catalog.CatalogService;
import com.flashsale.catalog.DropPhase;
import com.flashsale.catalog.DropRepository;
import com.flashsale.catalog.ProductRepository;
import com.flashsale.support.TestcontainersConfiguration;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DemoDropSeederTest {

    @Autowired
    private ProductRepository products;
    @Autowired
    private DropRepository drops;
    @Autowired
    private CatalogService catalog;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock clock;

    @BeforeEach
    void cleanDrops() {
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM drops");
    }

    @Test
    void seedsAFullScheduleOnceAndIsIdempotent() {
        DemoDropSeeder seeder = new DemoDropSeeder(products, drops, clock);

        assertThat(seeder.seedIfNeeded()).isEqualTo(8);
        assertThat(seeder.seedIfNeeded()).isZero();

        assertThat(catalog.listDrops(DropPhase.LIVE)).hasSize(2);
        assertThat(catalog.listDrops(DropPhase.UPCOMING)).hasSize(5);
        assertThat(catalog.listDrops(DropPhase.ENDED)).hasSize(1);
    }

    @Test
    void topsUpOnlyTheProductsMissingACurrentDrop() {
        DemoDropSeeder seeder = new DemoDropSeeder(products, drops, clock);
        seeder.seedIfNeeded();
        long pipDropId = jdbc.queryForObject(DROPS_OF_PRODUCT.replace("count(*)", "d.id"), Long.class, "FF-THIS-002");

        // Pip Thistle's drop ends; the next run schedules a new one for it and nothing else.
        jdbc.update("UPDATE drops SET start_time = now() - interval '2 hours', end_time = now() - interval '1 hour' WHERE id = ?",
                pipDropId);

        assertThat(seeder.seedIfNeeded()).isEqualTo(1);
        assertThat(jdbc.queryForObject(DROPS_OF_PRODUCT, Integer.class, "FF-THIS-002")).isEqualTo(2);
    }

    @Test
    void neverRecreatesTheHistoricalDrop() {
        DemoDropSeeder seeder = new DemoDropSeeder(products, drops, clock);
        seeder.seedIfNeeded();
        seeder.seedIfNeeded();

        assertThat(jdbc.queryForObject(DROPS_OF_PRODUCT, Integer.class, "SB-CORA-001")).isEqualTo(1);
    }

    private static final String DROPS_OF_PRODUCT =
            "SELECT count(*) FROM drops d JOIN products p ON p.id = d.product_id WHERE p.sku = ?";
}
