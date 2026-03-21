package com.proteinlens.ingestionservice.service;

import com.proteinlens.ingestionservice.dto.StringDbInteractionDto;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StringDbClientTest {

    private MockWebServer mockWebServer;
    private StringDbClient stringDbClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        stringDbClient = new StringDbClient(webClient);
        // Inject @Value fields via reflection or use a Spring context — simplified here
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void fetchNetwork_returnsParsedInteractions() {
        String responseBody = """
                [
                  {
                    "stringId_A": "9606.ENSP00000269305",
                    "stringId_B": "9606.ENSP00000306245",
                    "preferredName_A": "TP53",
                    "preferredName_B": "MDM2",
                    "ncbiTaxonId": 9606,
                    "score": 0.999,
                    "nscore": 0.0,
                    "fscore": 0.0,
                    "pscore": 0.0,
                    "ascore": 0.0,
                    "escore": 0.999,
                    "dscore": 0.0,
                    "tscore": 0.0
                  }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .addHeader("Content-Type", "application/json"));

        Mono<List<StringDbInteractionDto>> result = stringDbClient.fetchNetwork(
                List.of("TP53", "MDM2"), 9606, 400);

        StepVerifier.create(result)
                .assertNext(interactions -> {
                    assertThat(interactions).hasSize(1);
                    StringDbInteractionDto dto = interactions.get(0);
                    assertThat(dto.getPreferredNameA()).isEqualTo("TP53");
                    assertThat(dto.getPreferredNameB()).isEqualTo("MDM2");
                    assertThat(dto.getScore()).isEqualTo(0.999);
                })
                .verifyComplete();
    }
}
