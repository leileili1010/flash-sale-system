package com.flashsale.order;

import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kafka wiring for the order pipeline.
 *
 * <ul>
 *   <li>Events are plain JSON without type headers, so producers and consumers share a
 *       contract, not Java class names.</li>
 *   <li>The producer is idempotent with {@code acks=all} (see application.yml): an
 *       acknowledged event survives a broker failure and is written once per send.</li>
 *   <li>Failed records are retried a few times, then parked on a dead-letter topic with the
 *       original bytes, so one poison message never blocks a partition.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class OrderMessagingConfig {

    public static final String ORDER_REQUESTS_TOPIC = "orders.requested";
    public static final String DEAD_LETTER_SUFFIX = ".DLT";
    static final int PARTITIONS = 6;

    @Bean
    NewTopic orderRequestsTopic() {
        return TopicBuilder.name(ORDER_REQUESTS_TOPIC).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic orderRequestsDeadLetterTopic() {
        return TopicBuilder.name(ORDER_REQUESTS_TOPIC + DEAD_LETTER_SUFFIX).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    ProducerFactory<String, Object> producerFactory(KafkaProperties properties, KafkaConnectionDetails connection,
                                                    JsonMapper jsonMapper) {
        Serializer<Object> json = new JacksonJsonSerializer<>(jsonMapper).noTypeInfo();
        // Dead-lettered records that failed deserialization are forwarded as their original bytes.
        DelegatingByTypeSerializer values = new DelegatingByTypeSerializer(
                Map.of(byte[].class, new ByteArraySerializer(), Object.class, json), true);
        Map<String, Object> config = properties.buildProducerProperties();
        config.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, connection.getProducer().getBootstrapServers());
        return new DefaultKafkaProducerFactory<>(config, new StringSerializer(), values);
    }

    @Bean
    KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    ConsumerFactory<String, OrderRequestedEvent> orderRequestConsumerFactory(KafkaProperties properties,
                                                                             KafkaConnectionDetails connection,
                                                                             JsonMapper jsonMapper) {
        var values = new ErrorHandlingDeserializer<>(
                new JacksonJsonDeserializer<>(OrderRequestedEvent.class, jsonMapper));
        Map<String, Object> config = properties.buildConsumerProperties();
        config.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, connection.getConsumer().getBootstrapServers());
        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), values);
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 3));
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, OrderRequestedEvent> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<String, OrderRequestedEvent> orderRequestConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, OrderRequestedEvent>();
        configurer.configure(castFactory(factory), castConsumerFactory(orderRequestConsumerFactory));
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentKafkaListenerContainerFactory<Object, Object> castFactory(
            ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
        return (ConcurrentKafkaListenerContainerFactory<Object, Object>) factory;
    }

    @SuppressWarnings("unchecked")
    private static ConsumerFactory<Object, Object> castConsumerFactory(ConsumerFactory<?, ?> factory) {
        return (ConsumerFactory<Object, Object>) factory;
    }
}
