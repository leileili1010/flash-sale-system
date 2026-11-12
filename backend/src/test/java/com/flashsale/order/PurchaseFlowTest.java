package com.flashsale.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashsale.catalog.Drop;
import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.inventory.InventoryPreheater;
import com.flashsale.inventory.InventoryService;
import com.flashsale.support.TestData;
import com.flashsale.support.TestcontainersConfiguration;
import com.flashsale.user.User;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestData.class})
class PurchaseFlowTest {

    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestData data;
    @Autowired
    private InventoryPreheater preheater;
    @Autowired
    private InventoryService inventory;
    @Autowired
    private PurchaseService purchases;
    @Autowired
    private KafkaTemplate<String, Object> kafka;
    @Autowired
    private KafkaProperties kafkaProperties;
    @Autowired
    private KafkaConnectionDetails kafkaConnection;
    @Autowired
    private JsonMapper json;

    private Drop live;
    private User shopper;
    private String token;

    @BeforeEach
    void setUp() {
        data.reset();
        live = data.drop(Instant.now().minusSeconds(60), Instant.now().plus(Duration.ofHours(1)), 5);
        preheater.preheat();
        shopper = data.user();
        token = data.tokenFor(shopper);
    }

    @Test
    void purchaseIsAcceptedThenBecomesAPendingOrder() throws Exception {
        String orderNo = purchase(live.getId(), token)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/orders/")))
                .andReturn().getResponse().getContentAsString();
        String number = json.readTree(orderNo).get("orderNo").asString();

        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                mockMvc.perform(get("/api/orders/{n}", number).header("Authorization", token))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                        .andExpect(jsonPath("$.amount").value(11.99)));

        assertThat(data.stock(live.getId())).isEqualTo(new TestData.StockRow(4, 1, 0));
        assertThat(inventory.availableStock(live.getId())).hasValue(4);
        mockMvc.perform(get("/api/orders").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void customersCanBuyOnlyOnce() throws Exception {
        purchase(live.getId(), token).andExpect(status().isAccepted());
        purchase(live.getId(), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_PURCHASED"));
    }

    @Test
    void rejectsPurchasesOutsideTheReleaseWindow() throws Exception {
        Drop upcoming = data.drop(Instant.now().plus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(2)), 5);
        preheater.preheat();

        purchase(upcoming.getId(), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DROP_NOT_STARTED"));
        purchase(999_999L, token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void purchasingRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/drops/{id}/purchase", live.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ordersAreVisibleOnlyToTheirOwner() throws Exception {
        String body = purchase(live.getId(), token).andReturn().getResponse().getContentAsString();
        String number = json.readTree(body).get("orderNo").asString();
        String stranger = data.tokenFor(data.user());

        mockMvc.perform(get("/api/orders/{n}", number).header("Authorization", stranger))
                .andExpect(status().isNotFound());
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                assertThat(data.countOrders(live.getId(), "PENDING_PAYMENT")).isEqualTo(1));
        mockMvc.perform(get("/api/orders/{n}", number).header("Authorization", stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentShoppersCreateExactlyAsManyOrdersAsThereIsStock() throws Exception {
        int stock = 20;
        int shoppers = 200;
        Drop hot = data.drop(Instant.now().minusSeconds(1), Instant.now().plus(Duration.ofHours(1)), stock);
        preheater.preheat();
        List<User> buyers = new ArrayList<>();
        for (int i = 0; i < shoppers; i++) {
            buyers.add(data.user());
        }

        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<ErrorCode>> results = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (User buyer : buyers) {
                results.add(executor.submit(() -> {
                    startGun.await();
                    try {
                        purchases.purchase(hot.getId(), buyer.getId());
                        return null;
                    } catch (ApiException ex) {
                        return ex.errorCode();
                    }
                }));
            }
            startGun.countDown();
        }

        int accepted = 0;
        Map<ErrorCode, Integer> rejected = new EnumMap<>(ErrorCode.class);
        for (Future<ErrorCode> result : results) {
            ErrorCode code = result.get();
            if (code == null) {
                accepted++;
            } else {
                rejected.merge(code, 1, Integer::sum);
            }
        }

        assertThat(accepted).isEqualTo(stock);
        assertThat(rejected).containsOnlyKeys(ErrorCode.SOLD_OUT).containsEntry(ErrorCode.SOLD_OUT, shoppers - stock);
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                assertThat(data.countOrders(hot.getId(), "PENDING_PAYMENT")).isEqualTo(stock));
        assertThat(data.countOrders(hot.getId(), "FAILED")).isZero();
        assertThat(data.stock(hot.getId())).isEqualTo(new TestData.StockRow(0, stock, 0));
    }

    @Test
    void malformedEventsAreParkedOnTheDeadLetterTopic() {
        String marker = "not-json-" + UUID.randomUUID();
        kafka.send(OrderMessagingConfig.ORDER_REQUESTS_TOPIC, "poison", marker.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConnection.getConsumer().getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-inspector-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, byte[]> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new ByteArrayDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(OrderMessagingConfig.ORDER_REQUESTS_TOPIC + OrderMessagingConfig.DEAD_LETTER_SUFFIX));
            await().atMost(ASYNC_TIMEOUT).until(() -> {
                for (ConsumerRecord<String, byte[]> record : KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(1))) {
                    if (new String(record.value(), StandardCharsets.UTF_8).equals(marker)) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    private org.springframework.test.web.servlet.ResultActions purchase(long dropId, String bearer) throws Exception {
        return mockMvc.perform(post("/api/drops/{id}/purchase", dropId).header("Authorization", bearer));
    }
}
