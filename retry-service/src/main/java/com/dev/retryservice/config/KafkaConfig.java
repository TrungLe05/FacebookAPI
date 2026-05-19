package com.dev.retryservice.config;

import com.dev.retryservice.Models.SendFailedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // ── Consumer: send_failed ──────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, SendFailedEvent> sendFailedConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "retry-service-group");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // ErrorHandlingDeserializer bọc ngoài
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS,
                StringDeserializer.class.getName());
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS,
                JsonDeserializer.class.getName());

        // Không dùng type header — tự map về class theo VALUE_DEFAULT_TYPE
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                SendFailedEvent.class.getName());
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);  // ← bỏ qua type header từ producer

        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SendFailedEvent>
    sendFailedListenerFactory(
            ConsumerFactory<String, SendFailedEvent> sendFailedConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, SendFailedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sendFailedConsumerFactory);
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    // ── Producer: send_retry + dead_letter ────────────────────────────────

    @Bean
    public ProducerFactory<String, SendFailedEvent> retryProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, SendFailedEvent> retryKafkaTemplate(
            ProducerFactory<String, SendFailedEvent> retryProducerFactory) {
        return new KafkaTemplate<>(retryProducerFactory);
    }
}
