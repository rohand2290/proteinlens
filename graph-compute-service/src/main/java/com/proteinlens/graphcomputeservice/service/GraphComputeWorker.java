package com.proteinlens.graphcomputeservice.service;

import com.proteinlens.graphcomputeservice.dto.ComputeJobDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes ingestion events from Kafka and triggers graph computation.
 *
 * Only processes SUCCESS events — FAILURE events are logged and skipped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphComputeWorker {

    private final Neo4jWriterService neo4jWriterService;

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

        log.info("Starting compute for job={}, proteins={}, interactions={}",
                job.getJobId(), job.getProteinsIngested(), job.getInteractionsIngested());

        try {
            // TODO: load adjacency matrix from Neo4j using job.getIdentifiers()
            // TODO: run graph algorithms via EJML (e.g. centrality, clustering)
            // TODO: pass results to neo4jWriterService

            log.info("Compute complete for job={}", job.getJobId());
        } catch (Exception e) {
            log.error("Compute failed for job={}: {}", job.getJobId(), e.getMessage(), e);
            // Re-throw to trigger Kafka retry/DLQ if configured
            throw e;
        }
    }
}
