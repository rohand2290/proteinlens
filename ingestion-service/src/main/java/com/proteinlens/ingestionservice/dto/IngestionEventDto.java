package com.proteinlens.ingestionservice.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Kafka event published after a successful ingestion run.
 * Downstream services (e.g. analysis-service) can consume this
 * to trigger further processing.
 */
@Data
@Builder
public class IngestionEventDto {

    public enum Status { SUCCESS, PARTIAL_FAILURE, FAILURE }

    private String jobId;
    private Status status;
    private List<String> identifiers;
    private Integer speciesTaxonId;
    private int interactionsIngested;
    private int proteinsIngested;
    private Instant timestamp;
    private String errorMessage;
}
