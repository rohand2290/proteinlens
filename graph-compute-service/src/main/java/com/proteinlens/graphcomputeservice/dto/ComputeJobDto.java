package com.proteinlens.graphcomputeservice.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Mirrors the IngestionEventDto published by ingestion-service onto ingestion.events.
 * Only SUCCESS events should trigger computation.
 */
@Data
public class ComputeJobDto {

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
