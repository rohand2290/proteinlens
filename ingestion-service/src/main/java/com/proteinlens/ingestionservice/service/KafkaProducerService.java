package com.proteinlens.ingestionservice.service;

import com.proteinlens.ingestionservice.dto.IngestionEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, IngestionEventDto> ingestionKafkaTemplate;

    @Value("${kafka.topics.ingestion-events}")
    private String ingestionEventsTopic;

    /**
     * Publishes an ingestion event to Kafka.
     * Uses the jobId as the message key so events for the same job
     * land on the same partition (preserving order).
     *
     * @param event the event to publish
     * @return a CompletableFuture that resolves once the broker acknowledges
     */
    public CompletableFuture<SendResult<String, IngestionEventDto>> publishIngestionEvent(
            IngestionEventDto event) {

        log.info("Publishing ingestion event: jobId={}, status={}, interactions={}",
                event.getJobId(), event.getStatus(), event.getInteractionsIngested());

        CompletableFuture<SendResult<String, IngestionEventDto>> future =
                ingestionKafkaTemplate.send(ingestionEventsTopic, event.getJobId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish ingestion event for jobId={}: {}",
                        event.getJobId(), ex.getMessage());
            } else {
                log.debug("Ingestion event published: jobId={}, partition={}, offset={}",
                        event.getJobId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });

        return future;
    }
}
