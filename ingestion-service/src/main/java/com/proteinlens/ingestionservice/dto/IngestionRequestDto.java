package com.proteinlens.ingestionservice.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Request body for POST /api/v1/ingest
 */
@Data
public class IngestionRequestDto {

    /** Protein identifiers (gene names, UniProt IDs, or ENSP IDs) */
    @NotEmpty(message = "At least one identifier is required")
    private List<String> identifiers;

    /** NCBI taxon ID — defaults to Homo sapiens (9606) if not provided */
    private Integer speciesTaxonId;

    /** Minimum interaction confidence score (0–1000) — null uses application default */
    private Integer requiredScore;
}
