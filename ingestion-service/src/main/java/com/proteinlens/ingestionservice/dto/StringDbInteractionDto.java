package com.proteinlens.ingestionservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Maps one entry from the STRING-DB /api/json/network or
 * /api/json/interaction_partners response array.
 */
@Data
public class StringDbInteractionDto {

    @JsonProperty("stringId_A")
    private String stringIdA;

    @JsonProperty("stringId_B")
    private String stringIdB;

    @JsonProperty("preferredName_A")
    private String preferredNameA;

    @JsonProperty("preferredName_B")
    private String preferredNameB;

    @JsonProperty("ncbiTaxonId")
    private Integer ncbiTaxonId;

    /** Combined confidence score (0–1000) */
    @JsonProperty("score")
    private Double score;

    @JsonProperty("nscore")
    private Double nscore;

    @JsonProperty("fscore")
    private Double fscore;

    @JsonProperty("pscore")
    private Double pscore;

    @JsonProperty("ascore")
    private Double ascore;

    @JsonProperty("escore")
    private Double escore;

    @JsonProperty("dscore")
    private Double dscore;

    @JsonProperty("tscore")
    private Double tscore;
}
