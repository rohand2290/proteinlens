package com.proteinlens.graphcomputeservice.config;

import com.proteinlens.graphcomputeservice.dto.ComputeJobDto;
import com.proteinlens.graphcomputeservice.service.GraphTooLargeException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@Profile("!cli")
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${kafka.topics.ingestion-events}")
    private String ingestionEventsTopic;

    @Bean
    public NewTopic ingestionEventsDlqTopic() {
        // Same partition count as ingestion.events so the recoverer can keep partition numbers aligned.
        return TopicBuilder.name(ingestionEventsTopic + ".dlq").partitions(3).replicas(1).build();
    }

    @Bean
    public ConsumerFactory<String, ComputeJobDto> consumerFactory() {
        JsonDeserializer<ComputeJobDto> json = new JsonDeserializer<>(ComputeJobDto.class, false);
        json.addTrustedPackages("com.proteinlens.*");

        // ErrorHandlingDeserializer turns an undeserializable (poison) record into a handled failure
        // instead of a container-level loop that never advances past it.
        return new DefaultKafkaConsumerFactory<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        ConsumerConfig.GROUP_ID_CONFIG, groupId,
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
                ),
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(json)
        );
    }

    /** Producer for DLQ records whose payload deserialized into a ComputeJobDto. */
    @Bean
    public KafkaTemplate<String, Object> dlqJsonTemplate() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new JsonSerializer<>()));
    }

    /** Producer for DLQ records that failed deserialization and carry the original raw bytes. */
    @Bean
    public KafkaTemplate<String, byte[]> dlqBytesTemplate() {
        Map<String, Object> props = Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new ByteArraySerializer()));
    }

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> dlqJsonTemplate,
                                            KafkaTemplate<String, byte[]> dlqBytesTemplate) {
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, dlqBytesTemplate);
        templates.put(Object.class, dlqJsonTemplate);

        var recoverer = new DeadLetterPublishingRecoverer(templates,
                (rec, ex) -> new TopicPartition(rec.topic() + ".dlq", rec.partition()));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(2_000L, 3));
        handler.addNotRetryableExceptions(GraphTooLargeException.class, IllegalArgumentException.class);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ComputeJobDto> kafkaListenerContainerFactory(
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ComputeJobDto> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
