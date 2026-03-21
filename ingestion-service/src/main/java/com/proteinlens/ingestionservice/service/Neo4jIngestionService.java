package com.proteinlens.ingestionservice.service;

import com.proteinlens.ingestionservice.dto.StringDbInteractionDto;
import com.proteinlens.ingestionservice.repository.InteractionRepository;
import com.proteinlens.ingestionservice.repository.ProteinRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persists STRING-DB interaction data into Neo4j.
 *
 * Strategy: MERGE on both Protein nodes and the INTERACTS_WITH relationship
 * so repeated ingestion of the same data is idempotent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jIngestionService {

    private final ProteinRepository proteinRepository;
    private final InteractionRepository interactionRepository;

    /**
     * Persists a batch of STRING-DB interactions.
     *
     * @return number of interaction edges written
     */
    @Transactional("transactionManager")
    public int persist(List<StringDbInteractionDto> interactions) {
        if (interactions.isEmpty()) {
            log.warn("No interactions to persist");
            return 0;
        }

        // 1. Upsert all protein nodes first (both sides of each edge)
        interactions.forEach(dto -> {
            proteinRepository.mergeProtein(dto.getStringIdA(), dto.getPreferredNameA(), dto.getNcbiTaxonId());
            proteinRepository.mergeProtein(dto.getStringIdB(), dto.getPreferredNameB(), dto.getNcbiTaxonId());
        });

        // 2. Upsert all interaction edges
        interactions.forEach(dto -> interactionRepository.mergeInteraction(
                dto.getStringIdA(),
                dto.getStringIdB(),
                safe(dto.getScore()),
                safe(dto.getNscore()),
                safe(dto.getFscore()),
                safe(dto.getPscore()),
                safe(dto.getAscore()),
                safe(dto.getEscore()),
                safe(dto.getDscore()),
                safe(dto.getTscore())
        ));

        log.info("Persisted {} interaction(s) to Neo4j", interactions.size());
        return interactions.size();
    }

    /** Counts distinct protein nodes referenced in this batch. */
    public int countDistinctProteins(List<StringDbInteractionDto> interactions) {
        return (int) interactions.stream()
                .flatMap(dto -> java.util.stream.Stream.of(dto.getStringIdA(), dto.getStringIdB()))
                .distinct()
                .count();
    }

    private double safe(Double value) {
        return value != null ? value : 0.0;
    }
}
