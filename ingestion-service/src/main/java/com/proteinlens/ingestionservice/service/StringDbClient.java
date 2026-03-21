package com.proteinlens.ingestionservice.service;

import com.proteinlens.ingestionservice.dto.StringDbInteractionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Thin client wrapping the STRING-DB REST API.
 *
 * Relevant STRING-DB endpoints used here:
 *   POST /json/network               — network for a set of proteins
 *   POST /json/interaction_partners  — interaction partners of a protein
 *
 * STRING-DB API docs: https://string-db.org/help/api/
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StringDbClient {

    private final WebClient stringDbWebClient;

    @Value("${stringdb.default-species}")
    private int defaultSpecies;

    @Value("${stringdb.required-score}")
    private int requiredScore;

    @Value("${stringdb.network-type}")
    private String networkType;

    @Value("${stringdb.caller-identity}")
    private String callerIdentity;

    private static final ParameterizedTypeReference<List<StringDbInteractionDto>> INTERACTION_LIST =
            new ParameterizedTypeReference<>() {};

    /**
     * Fetches the interaction network for a list of protein identifiers.
     * Uses POST to support large identifier sets.
     *
     * @param identifiers  gene names, UniProt IDs, or STRING IDs
     * @param speciesTaxonId NCBI taxon ID (e.g. 9606 for human); null uses default
     * @param minScore     minimum combined confidence score (0–1000); null uses default
     */
    public Mono<List<StringDbInteractionDto>> fetchNetwork(
            List<String> identifiers,
            Integer speciesTaxonId,
            Integer minScore) {

        String joinedIds = String.join("%0d", identifiers); // STRING-DB uses %0d as separator
        int species = speciesTaxonId != null ? speciesTaxonId : defaultSpecies;
        int score = minScore != null ? minScore : requiredScore;

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("identifiers", joinedIds);
        formData.add("species", String.valueOf(species));
        formData.add("required_score", String.valueOf(score));
        formData.add("network_type", networkType);
        formData.add("caller_identity", callerIdentity);

        log.debug("Fetching STRING-DB network for {} identifiers, species={}, minScore={}",
                identifiers.size(), species, score);

        return stringDbWebClient.post()
                .uri("/json/network")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(INTERACTION_LIST)
                .doOnSuccess(list -> log.debug("Received {} interactions from STRING-DB", list.size()))
                .doOnError(e -> log.error("STRING-DB network request failed: {}", e.getMessage()));
    }

    /**
     * Fetches interaction partners for a single protein.
     *
     * @param identifier   gene name, UniProt ID, or STRING ID
     * @param speciesTaxonId NCBI taxon ID; null uses default
     * @param minScore     minimum confidence score; null uses default
     * @param limit        max number of partners to return (STRING-DB default is 10)
     */
    public Mono<List<StringDbInteractionDto>> fetchInteractionPartners(
            String identifier,
            Integer speciesTaxonId,
            Integer minScore,
            Integer limit) {

        int species = speciesTaxonId != null ? speciesTaxonId : defaultSpecies;
        int score = minScore != null ? minScore : requiredScore;

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("identifiers", identifier);
        formData.add("species", String.valueOf(species));
        formData.add("required_score", String.valueOf(score));
        formData.add("caller_identity", callerIdentity);
        if (limit != null) {
            formData.add("limit", String.valueOf(limit));
        }

        log.debug("Fetching STRING-DB interaction partners for '{}', species={}", identifier, species);

        return stringDbWebClient.post()
                .uri("/json/interaction_partners")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(INTERACTION_LIST)
                .doOnSuccess(list -> log.debug("Received {} partners for '{}'", list.size(), identifier))
                .doOnError(e -> log.error("STRING-DB partners request failed for '{}': {}", identifier, e.getMessage()));
    }
}
