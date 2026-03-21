package com.proteinlens.graphcomputeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Writes computed graph metrics back to Neo4j as properties on Protein nodes.
 *
 * Uses Neo4jClient for raw Cypher queries rather than the repository abstraction,
 * since computed results are bulk-written by STRING ID.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jWriterService {

    private final Neo4jClient neo4jClient;

    /**
     * Writes a computed metric (e.g. centrality score) to a Protein node.
     *
     * @param stringId  STRING stable ID of the protein (e.g. "9606.ENSP00000269305")
     * @param property  name of the property to set on the node
     * @param value     computed value
     */
    public void writeProteinMetric(String stringId, String property, double value) {
        // Dynamic property names are not injectable as parameters in Cypher,
        // so the property name is validated here before interpolation.
        if (!property.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid property name: " + property);
        }

        neo4jClient.query("MATCH (p:Protein {stringId: $stringId}) SET p." + property + " = $value")
                .bindAll(Map.of("stringId", stringId, "value", value))
                .run();

        log.debug("Set {}={} on protein {}", property, value, stringId);
    }
}
