package com.flashsale.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.support.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private DropRepository drops;
    @Autowired
    private ProductRepository products;

    private Drop live;
    private Drop upcoming;
    private Drop ended;
    private Drop draft;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM drops");
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();

        Instant now = Instant.now();
        live = save("Live drop", DropStatus.PUBLISHED, now.minus(Duration.ofMinutes(5)), now.plus(Duration.ofHours(1)), 25);
        upcoming = save("Upcoming drop", DropStatus.PUBLISHED, now.plus(Duration.ofHours(1)), now.plus(Duration.ofHours(2)), 10);
        ended = save("Ended drop", DropStatus.PUBLISHED, now.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(1)), 5);
        draft = save("Draft drop", DropStatus.DRAFT, now.minus(Duration.ofMinutes(5)), now.plus(Duration.ofHours(1)), 5);
    }

    @Test
    void listsTheProductCatalogWithHttpCaching() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(header().string("Cache-Control", "max-age=60, public"));
    }

    @Test
    void returnsASingleProduct() throws Exception {
        long id = products.findBySku("FF-MOSS-001").orElseThrow().getId();
        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mochi Moss"))
                .andExpect(jsonPath("$.type").value("BLIND_BOX"));
    }

    @Test
    void filtersDropsByPhaseAndHidesUnpublishedDrops() throws Exception {
        mockMvc.perform(get("/api/drops").param("phase", "LIVE"))
                .andExpect(jsonPath("$[*].title", contains("Live drop")));
        mockMvc.perform(get("/api/drops").param("phase", "UPCOMING"))
                .andExpect(jsonPath("$[*].title", contains("Upcoming drop")));
        mockMvc.perform(get("/api/drops").param("phase", "ENDED"))
                .andExpect(jsonPath("$[*].title", contains("Ended drop")));
    }

    @Test
    void listsLiveThenUpcomingThenEndedWithoutAFilter() throws Exception {
        mockMvc.perform(get("/api/drops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].phase", contains("LIVE", "UPCOMING", "ENDED")));
    }

    @Test
    void returnsDropDetailWithPhaseAndStock() throws Exception {
        mockMvc.perform(get("/api/drops/{id}", live.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("LIVE"))
                .andExpect(jsonPath("$.availableStock").value(25))
                .andExpect(jsonPath("$.soldOut").value(false))
                .andExpect(jsonPath("$.product.sku").value("FF-MOSS-001"));
    }

    @Test
    void cachesStaticDropDetailsButAlwaysReadsLiveStock() throws Exception {
        mockMvc.perform(get("/api/drops/{id}", live.getId())).andExpect(status().isOk());
        assertThat(redis.hasKey("flashsale:cache:drop-info::" + live.getId())).isTrue();

        jdbc.update("UPDATE drops SET title = 'Renamed', available_stock = available_stock - 1, "
                + "locked_stock = locked_stock + 1 WHERE id = ?", live.getId());

        mockMvc.perform(get("/api/drops/{id}", live.getId()))
                .andExpect(jsonPath("$.title").value("Live drop"))
                .andExpect(jsonPath("$.availableStock").value(24));
    }

    @Test
    void unpublishedDropsAreNotFoundAndNotCached() throws Exception {
        mockMvc.perform(get("/api/drops/{id}", draft.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(redis.hasKey("flashsale:cache:drop-info::" + draft.getId())).isFalse();
    }

    @Test
    void rejectsAnUnknownPhase() throws Exception {
        mockMvc.perform(get("/api/drops").param("phase", "SOON"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private Drop save(String title, DropStatus status, Instant start, Instant end, int stock) {
        Product product = products.findBySku("FF-MOSS-001").orElseThrow();
        return drops.save(new Drop(product, title, new BigDecimal("9.99"), product.getRetailPrice(),
                status, start, end, stock, Instant.now()));
    }
}
