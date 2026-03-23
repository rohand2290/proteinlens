package com.proteinlens.graphcomputeservice.repository;

import com.proteinlens.graphcomputeservice.model.ProteinSubgraph;
import com.proteinlens.graphcomputeservice.model.SubgraphEdge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Reads protein interaction subgraphs from Neo4j for a given set of STRING identifiers.
 *
 * Uses Neo4jClient (raw Cypher) rather than the repository abstraction because the
 * result is not a managed entity — it is a lightweight projection used to build an
 * adjacency matrix for graph algorithm computation.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SubgraphRepository {

    private final Neo4jClient neo4jClient;

    /**
     * Loads the induced subgraph for the given STRING stable IDs: all matching Protein
     * nodes and all INTERACTS_WITH edges that exist between them.
     *
     * @param identifiers STRING stable IDs from the ingestion job (e.g. "9606.ENSP00000269305")
     * @return ProteinSubgraph with an ordered node list and weighted directed edges
     */
    public ProteinSubgraph loadSubgraph(List<String> identifiers) {
        List<String> nodeIds = neo4jClient
                .query("""
                        MATCH (p:Protein)
                        WHERE p.stringId IN $identifiers
                        RETURN p.stringId AS stringId
                        ORDER BY p.stringId
                        """)
                .bind(identifiers).to("identifiers")
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("stringId").asString())
                .all()
                .stream()
                .toList();

        List<SubgraphEdge> edges = neo4jClient
                .query("""
                        MATCH (a:Protein)-[r:INTERACTS_WITH]->(b:Protein)
                        WHERE a.stringId IN $identifiers AND b.stringId IN $identifiers
                        RETURN a.stringId AS source, b.stringId AS target, r.score AS score
                        """)
                .bind(identifiers).to("identifiers")
                .fetchAs(SubgraphEdge.class)
                .mappedBy((typeSystem, record) -> new SubgraphEdge(
                        record.get("source").asString(),
                        record.get("target").asString(),
                        record.get("score").asDouble()
                ))
                .all()
                .stream()
                .toList();

        log.debug("Loaded subgraph for {} identifiers: {} nodes, {} edges",
                identifiers.size(), nodeIds.size(), edges.size());

        return new ProteinSubgraph(nodeIds, edges);
    }
}
