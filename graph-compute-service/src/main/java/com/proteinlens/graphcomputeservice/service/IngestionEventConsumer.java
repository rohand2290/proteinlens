package com.proteinlens.graphcomputeservice.service;

import com.proteinlens.graphcomputeservice.dto.ComputeJobDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer for ingestion events. Filters non-SUCCESS events and delegates
 * processing to {@link GraphComputeWorker}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionEventConsumer {

    private final GraphComputeWorker graphComputeWorker;

    @KafkaListener(
            topics = "${kafka.topics.ingestion-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onIngestionEvent(ComputeJobDto job) {
        if (job.getStatus() != ComputeJobDto.Status.SUCCESS) {
            log.warn("Skipping job={} with status={}", job.getJobId(), job.getStatus());
            return;
        }

        graphComputeWorker.process(job);
    }
}
