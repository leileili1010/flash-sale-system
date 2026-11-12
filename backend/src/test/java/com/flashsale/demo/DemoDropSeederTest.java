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

        assertThat(seeder.seedIfNeeded()).isEqualTo(6);
        assertThat(seeder.seedIfNeeded()).isZero();

        assertThat(catalog.listDrops(DropPhase.LIVE)).hasSize(2);
        assertThat(catalog.listDrops(DropPhase.UPCOMING)).hasSize(3);
        assertThat(catalog.listDrops(DropPhase.ENDED)).hasSize(1);
    }
}
