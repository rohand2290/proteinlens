package com.proteinlens.ingestionservice.controller;

import com.proteinlens.ingestionservice.dto.IngestionEventDto;
import com.proteinlens.ingestionservice.dto.IngestionRequestDto;
import com.proteinlens.ingestionservice.service.IngestionOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionOrchestrator ingestionOrchestrator;

    /**
     * Triggers ingestion of protein interaction data from STRING-DB.
     *
     * Example request body:
     * {
     *   "identifiers": ["TP53", "MDM2", "BRCA1"],
     *   "speciesTaxonId": 9606,
     *   "requiredScore": 700
     * }
     */
    @PostMapping
    public ResponseEntity<IngestionEventDto> ingest(@Valid @RequestBody IngestionRequestDto request) {
        IngestionEventDto result = ingestionOrchestrator.ingest(request);
        return ResponseEntity.ok(result);
    }
}
