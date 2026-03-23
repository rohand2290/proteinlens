package com.proteinlens.ingestionservice.service;

import com.proteinlens.ingestionservice.dto.IngestionEventDto;
import com.proteinlens.ingestionservice.dto.IngestionRequestDto;
import com.proteinlens.ingestionservice.dto.StringDbInteractionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Orchestrates the full ingestion pipeline:
 *   1. Fetch interactions from STRING-DB
 *   2. Persist to Neo4j
 *   3. Publish Kafka event
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionOrchestrator {

    private final StringDbClient stringDbClient;
    private final Neo4jIngestionService neo4jIngestionService;
    private final KafkaProducerService kafkaProducerService;

    public IngestionEventDto ingest(IngestionRequestDto request) {
        String jobId = UUID.randomUUID().toString();
        log.info("Starting ingestion job={} for {} identifier(s)", jobId, request.getIdentifiers().size());

        try {
            // 1. Fetch from STRING-DB (block here — we're in a servlet thread)
            List<StringDbInteractionDto> interactions = stringDbClient
                    .fetchNetwork(request.getIdentifiers(), request.getSpeciesTaxonId(), request.getRequiredScore())
                    .block();

            if (interactions == null) {
                interactions = List.of();
            }

            // 2. Persist to Neo4j
            int interactionsWritten = neo4jIngestionService.persist(interactions);
            int proteinsWritten = neo4jIngestionService.countDistinctProteins(interactions);

            // 3. Collect the resolved STRING IDs that were actually written to Neo4j
            //    (original identifiers are gene names; Neo4j stores ENSP-based stringIds)
            List<String> resolvedStringIds = interactions.stream()
                    .flatMap(dto -> Stream.of(dto.getStringIdA(), dto.getStringIdB()))
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            // 4. Publish event
            IngestionEventDto event = IngestionEventDto.builder()
                    .jobId(jobId)
                    .status(IngestionEventDto.Status.SUCCESS)
                    .identifiers(resolvedStringIds)
                    .speciesTaxonId(request.getSpeciesTaxonId())
                    .interactionsIngested(interactionsWritten)
                    .proteinsIngested(proteinsWritten)
                    .timestamp(Instant.now())
                    .build();

            kafkaProducerService.publishIngestionEvent(event);

            log.info("Ingestion job={} completed: {} interactions, {} proteins", jobId, interactionsWritten, proteinsWritten);
            return event;

        } catch (Exception e) {
            log.error("Ingestion job={} failed: {}", jobId, e.getMessage(), e);

            IngestionEventDto failureEvent = IngestionEventDto.builder()
                    .jobId(jobId)
                    .status(IngestionEventDto.Status.FAILURE)
                    .identifiers(request.getIdentifiers())
                    .speciesTaxonId(request.getSpeciesTaxonId())
                    .interactionsIngested(0)
                    .proteinsIngested(0)
                    .timestamp(Instant.now())
                    .errorMessage(e.getMessage())
                    .build();

            kafkaProducerService.publishIngestionEvent(failureEvent);
            throw new RuntimeException("Ingestion failed for jobId=" + jobId, e);
        }
    }
}
