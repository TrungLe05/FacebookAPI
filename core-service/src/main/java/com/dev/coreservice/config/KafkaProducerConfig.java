package com.dev.coreservice.config;

import com.dev.coreservice.model.NormalizedEvent;
import com.dev.coreservice.model.ReplyCommand;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    private Map<String, Object> baseConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return config;
    }

    // ── KafkaTemplate<String, ReplyCommand> ───────────────────────────────
    // Dùng bởi ReplyCommandPublisher → publish reply_commands

    @Bean
    public ProducerFactory<String, ReplyCommand> replyCommandProducerFactory() {
        return new DefaultKafkaProducerFactory<>(baseConfig());
    }

    @Bean
    public KafkaTemplate<String, ReplyCommand> replyCommandKafkaTemplate(
            ProducerFactory<String, ReplyCommand> replyCommandProducerFactory) {
        return new KafkaTemplate<>(replyCommandProducerFactory);
    }

    // ── KafkaTemplate<String, NormalizedEvent> ────────────────────────────
    // Dùng bởi RawEventConsumer → publish dead_letter_events khi xử lý lỗi

    @Bean
    public ProducerFactory<String, NormalizedEvent> normalizedEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(baseConfig());
    }

    @Bean
    public KafkaTemplate<String, NormalizedEvent> normalizedEventKafkaTemplate(
            ProducerFactory<String, NormalizedEvent> normalizedEventProducerFactory) {
        return new KafkaTemplate<>(normalizedEventProducerFactory);
    }
}
