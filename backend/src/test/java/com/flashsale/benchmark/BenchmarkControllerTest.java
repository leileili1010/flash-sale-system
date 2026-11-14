package com.flashsale.benchmark;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.catalog.Drop;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("benchmark")
@Import({TestcontainersConfiguration.class, TestData.class})
class BenchmarkControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestData data;
    @Autowired
    private JsonMapper json;

    @BeforeEach
    void setUp() {
        data.reset();
    }

    @Test
    void mintsWorkingShopperTokens() throws Exception {
        String body = mockMvc.perform(post("/api/demo/users").param("run", "test-run").param("count", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokens.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).get("tokens").get(0).asString();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test-run-0@loadtest.local"));
    }

    @Test
    void naiveEndpointRecordsAcceptedPurchasesAndStopsAtZeroSequentially() throws Exception {
        Drop drop = data.drop(Instant.now().minusSeconds(60), Instant.now().plus(Duration.ofHours(1)), 2);

        mockMvc.perform(post("/api/demo/naive-purchase/{id}", drop.getId())).andExpect(status().isOk());
        mockMvc.perform(post("/api/demo/naive-purchase/{id}", drop.getId())).andExpect(status().isOk());
        mockMvc.perform(post("/api/demo/naive-purchase/{id}", drop.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOLD_OUT"));

        mockMvc.perform(get("/api/demo/naive-purchase/{id}", drop.getId()))
                .andExpect(jsonPath("$.acceptedPurchases").value(2))
                .andExpect(jsonPath("$.oversold").value(0));
    }

    @Test
    void rejectsOversizedBatches() throws Exception {
        mockMvc.perform(post("/api/demo/users").param("run", "x").param("count", "5000"))
                .andExpect(status().isBadRequest());
    }
}
