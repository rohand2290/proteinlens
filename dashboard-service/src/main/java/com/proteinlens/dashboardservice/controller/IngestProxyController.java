package com.proteinlens.dashboardservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * Proxies POST /api/ingest to ingestion-service POST /api/v1/ingest.
 * Forwards the upstream HTTP status and body so the frontend sees meaningful errors.
 */
@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class IngestProxyController {

    private final WebClient ingestionWebClient;

    @PostMapping
    public ResponseEntity<String> ingest(@RequestBody Map<String, Object> body) {
        try {
            String response = ingestionWebClient.post()
                    .uri("/api/v1/ingest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);

        } catch (WebClientResponseException ex) {
            // Forward the upstream status code and body verbatim
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }
}
