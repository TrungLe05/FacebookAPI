package com.dev.coreservice.config;

import com.dev.coreservice.model.NormalizedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:core-service-group}")
    private String groupId;

    /**
     * ConsumerFactory cho NormalizedEvent.
     * Dùng JsonDeserializer với trusted packages để tránh lỗi class-not-trusted.
     */
    @Bean
    public ConsumerFactory<String, NormalizedEvent> consumerFactory() {
        JsonDeserializer<NormalizedEvent> deserializer = new JsonDeserializer<>(NormalizedEvent.class, false);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(false);

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Đọc nhiều record mỗi lần để tăng throughput khi có bài viral
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    /**
     * Listener container factory với 3 concurrent threads để xử lý song song.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NormalizedEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, NormalizedEvent> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, NormalizedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    /**
     * KafkaTemplate để publish dead_letter_events.
     */
    @Bean
    public KafkaTemplate<String, NormalizedEvent> kafkaTemplate(
            ProducerFactory<String, NormalizedEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
