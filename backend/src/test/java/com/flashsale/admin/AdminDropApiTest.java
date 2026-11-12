package com.flashsale.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.catalog.Drop;
import com.flashsale.catalog.ProductRepository;
import com.flashsale.inventory.InventoryService;
import com.flashsale.support.TestData;
import com.flashsale.support.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestData.class})
class AdminDropApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestData data;
    @Autowired
    private ProductRepository products;
    @Autowired
    private InventoryService inventory;
    @Autowired
    private JsonMapper json;

    private String admin;
    private long productId;

    @BeforeEach
    void setUp() {
        data.reset();
        admin = data.tokenFor(data.admin());
        productId = products.findBySku("FF-THIS-002").orElseThrow().getId();
    }

    @Test
    void draftsAreHiddenFromShoppersUntilPublished() throws Exception {
        Instant start = Instant.now().plus(Duration.ofHours(1));
        long id = idOf(create("Pip Thistle Encore", start, start.plus(Duration.ofHours(2)), 25, false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.phase").value("UPCOMING"))
                .andExpect(jsonPath("$.availableStock").value(25)));

        mockMvc.perform(get("/api/drops")).andExpect(jsonPath("$[*].id", not(hasItem((int) id))));
        mockMvc.perform(get("/api/drops/{id}", id)).andExpect(status().isNotFound());
        assertThat(inventory.availableStock(id)).isEmpty();

        mockMvc.perform(post("/api/admin/drops/{id}/publish", id).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/drops/{id}", id)).andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(inventory.availableStock(id)).hasValue(25));
    }

    @Test
    void publishingImmediatelyMakesTheDropPurchasable() throws Exception {
        Instant start = Instant.now().minusSeconds(5);
        long id = idOf(create("Pip Thistle Flash", start, start.plus(Duration.ofHours(1)), 3, true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phase").value("LIVE")));

        String shopper = data.tokenFor(data.user());
        mockMvc.perform(post("/api/drops/{id}/purchase", id).header("Authorization", shopper))
                .andExpect(status().isAccepted());
        assertThat(inventory.availableStock(id)).hasValue(2);
    }

    @Test
    void cancellingAnUpcomingDropRemovesItEverywhere() throws Exception {
        Instant start = Instant.now().plus(Duration.ofHours(1));
        long id = idOf(create("Pip Thistle Preview", start, start.plus(Duration.ofHours(1)), 10, true));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(inventory.availableStock(id)).hasValue(10));
        mockMvc.perform(get("/api/drops/{id}", id)).andExpect(status().isOk()); // warms the cache

        mockMvc.perform(post("/api/admin/drops/{id}/cancel", id).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/drops/{id}", id)).andExpect(status().isNotFound());
        assertThat(inventory.availableStock(id)).isEmpty();
    }

    @Test
    void liveDropsCannotBeCancelled() throws Exception {
        Drop live = data.drop(Instant.now().minusSeconds(60), Instant.now().plus(Duration.ofHours(1)), 5);
        mockMvc.perform(post("/api/admin/drops/{id}/cancel", live.getId()).header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DROP_NOT_EDITABLE"));
    }

    @Test
    void rejectsInvalidSchedules() throws Exception {
        Instant start = Instant.now().plus(Duration.ofHours(1));
        create("Backwards", start, start.minusSeconds(60), 10, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.endTime").value("must be after the start time"));

        Instant past = Instant.now().minus(Duration.ofHours(2));
        create("In the past", past, past.plus(Duration.ofHours(1)), 10, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The start time must not be in the past."));
    }

    @Test
    void listsDraftsAndCancelledDropsForAdminsOnly() throws Exception {
        Instant start = Instant.now().plus(Duration.ofHours(1));
        create("Draft one", start, start.plus(Duration.ofHours(1)), 5, false);

        mockMvc.perform(get("/api/admin/drops").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status", hasItem("DRAFT")));
        mockMvc.perform(get("/api/admin/drops").header("Authorization", data.tokenFor(data.user())))
                .andExpect(status().isForbidden());
    }

    private ResultActions create(String title, Instant start, Instant end, int stock, boolean publish) throws Exception {
        String body = json.writeValueAsString(new CreateDropRequest(productId, title, new java.math.BigDecimal("12.49"),
                start, end, stock, publish));
        return mockMvc.perform(post("/api/admin/drops").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long idOf(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
