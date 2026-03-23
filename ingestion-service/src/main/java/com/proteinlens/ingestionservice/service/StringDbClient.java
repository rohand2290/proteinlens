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
 * Endpoint used: POST /json/network — interaction network for a set of proteins.
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

        String joinedIds = String.join("\r", identifiers); // STRING-DB expects identifiers separated by \r (%0d)
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

}
