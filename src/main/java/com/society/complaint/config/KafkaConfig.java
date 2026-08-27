package com.society.complaint.config;

import com.society.complaint.kafka.ComplaintEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka wiring — tells Spring how to:
 * 1. PRODUCE messages: serialize ComplaintEvent to JSON before sending
 * 2. CONSUME messages: deserialize JSON back to ComplaintEvent when receiving
 *
 * Plain English:
 * - ProducerFactory = settings for sending messages
 * - ConsumerFactory = settings for receiving messages
 * - KafkaTemplate = the object your code actually calls to send (uses ProducerFactory)
 * - KafkaListenerContainerFactory = the object that powers @KafkaListener (uses ConsumerFactory)
 */
@Configuration
@Profile("!prod")
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // =========================================================
    // PRODUCER configuration
    // =========================================================

    @Bean
    public ProducerFactory<String, ComplaintEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // Key is a plain String (complaintId), value is JSON-serialised ComplaintEvent
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Retry up to 3 times if the broker is temporarily unavailable
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, ComplaintEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // =========================================================
    // CONSUMER configuration
    // =========================================================

    @Bean
    public ConsumerFactory<String, ComplaintEvent> consumerFactory() {
        JsonDeserializer<ComplaintEvent> deserializer =
                new JsonDeserializer<>(ComplaintEvent.class);
        // Trust our own package — prevents deserialization errors
        deserializer.addTrustedPackages("com.society.complaint.kafka");

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // group-id: Kafka remembers which messages this group has already processed
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "email-group");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
        // earliest = if this consumer is new/restarted, start from the beginning
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(config,
                new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ComplaintEvent>
    kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ComplaintEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
