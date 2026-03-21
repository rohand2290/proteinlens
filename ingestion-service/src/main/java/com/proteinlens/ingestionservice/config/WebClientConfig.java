package com.proteinlens.ingestionservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${stringdb.base-url}")
    private String stringDbBaseUrl;

    // STRING-DB returns "text/json" instead of "application/json".
    // Register a decoder that accepts both so WebClient can deserialize the response.
    private static final MediaType TEXT_JSON = new MediaType("text", "json");

    @Bean
    public WebClient stringDbWebClient(WebClient.Builder builder, ObjectMapper objectMapper) {
        return builder
                .baseUrl(stringDbBaseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .codecs(config -> {
                    config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024);
                    config.defaultCodecs().jackson2JsonDecoder(
                            new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON, TEXT_JSON)
                    );
                })
                .build();
    }
}
