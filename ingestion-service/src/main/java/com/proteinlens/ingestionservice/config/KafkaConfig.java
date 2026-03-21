package com.proteinlens.ingestionservice.config;

import com.proteinlens.ingestionservice.dto.IngestionEventDto;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.ingestion-events}")
    private String ingestionEventsTopic;

    @Bean
    public NewTopic ingestionEventsTopic() {
        return TopicBuilder.name(ingestionEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public KafkaTemplate<String, IngestionEventDto> ingestionKafkaTemplate(
            ProducerFactory<String, IngestionEventDto> producerFactory) {
        return new KafkaTemplate<>(producerFactory, Map.of(
                JsonSerializer.ADD_TYPE_INFO_HEADERS, false
        ));
    }
}
