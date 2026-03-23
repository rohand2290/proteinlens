package com.proteinlens.graphcomputeservice.service;

import com.proteinlens.graphcomputeservice.dto.ComputeJobDto;
import com.proteinlens.graphcomputeservice.model.NodeIndexMap;
import com.proteinlens.graphcomputeservice.model.SpectralResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Persists spectral computation results back to Neo4j.
 *
 * Two writes are performed in a single transaction:
 *   1. Annotate each Protein node with clusterId and centralityScore.
 *   2. Create one SpectralResult node for the job and link it to every
 *      protein in the job's identifier list via COVERS relationships.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpectralResultWriter {

    private final Neo4jClient neo4jClient;

    /**
     * Writes spectral results for all proteins identified in the job DTO.
     *
     * @param job          the originating compute job (provides jobId and identifier list)
     * @param nodeIndexMap maps each protein stringId to its matrix row/column index
     * @param result       output of {@link SpectralAnalyzerService#analyze}
     */
    @Transactional
    public void write(ComputeJobDto job, NodeIndexMap nodeIndexMap, SpectralResult result) {
        List<Map<String, Object>> proteinRows = buildProteinRows(job, nodeIndexMap, result);

        annotateProteins(proteinRows);
        createSpectralResultNode(job, result, proteinRows);

        log.info("SpectralResultWriter: wrote {} protein annotations and SpectralResult node for job={}",
                proteinRows.size(), job.getJobId());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds one parameter map per protein that carries the stringId, its cluster
     * assignment, and its centrality score.  Only identifiers present in
     * nodeIndexMap are included — any stale identifiers in the DTO are skipped
     * with a warning rather than surfacing as an exception.
     */
    private List<Map<String, Object>> buildProteinRows(ComputeJobDto job,
                                                        NodeIndexMap nodeIndexMap,
                                                        SpectralResult result) {
        List<String> identifiers = job.getIdentifiers();
        List<Map<String, Object>> rows = new ArrayList<>(identifiers.size());

        for (String stringId : identifiers) {
            int idx;
            try {
                idx = nodeIndexMap.indexOf(stringId);
            } catch (IllegalArgumentException e) {
                log.warn("SpectralResultWriter: stringId={} not in nodeIndexMap for job={}, skipping",
                        stringId, job.getJobId());
                continue;
            }

            rows.add(Map.of(
                    "stringId",       stringId,
                    "clusterId",      result.clusterAssignments()[idx],
                    "centralityScore", result.centrality()[idx]
            ));
        }
        return rows;
    }

    /**
     * Bulk-sets clusterId and centralityScore on every matched Protein node.
     */
    private void annotateProteins(List<Map<String, Object>> proteinRows) {
        neo4jClient.query("""
                UNWIND $proteins AS row
                MATCH (p:Protein {stringId: row.stringId})
                SET p.clusterId       = row.clusterId,
                    p.centralityScore = row.centralityScore
                """)
                .bindAll(new HashMap<>(Map.of("proteins", proteinRows)))
                .run();
    }

    /**
     * Creates one :SpectralResult node and attaches it to every protein in
     * proteinRows via a COVERS relationship.
     */
    private void createSpectralResultNode(ComputeJobDto job,
                                          SpectralResult result,
                                          List<Map<String, Object>> proteinRows) {
        List<String> stringIds = proteinRows.stream()
                .map(row -> (String) row.get("stringId"))
                .toList();

        List<Double> eigenvalueList = Arrays.stream(result.eigenvalues())
                .boxed()
                .collect(Collectors.toList());

        Map<String, Object> params = new HashMap<>();
        params.put("jobId",       job.getJobId());
        params.put("kStar",       result.kStar());
        params.put("maxGap",      result.maxGap());
        params.put("convergedIn", result.convergedIn());
        params.put("createdAt",   Instant.now().toString());
        params.put("stringIds",   stringIds);
        params.put("eigenvalues", eigenvalueList);

        neo4jClient.query("""
                CREATE (sr:SpectralResult {
                    jobId:       $jobId,
                    kStar:       $kStar,
                    maxGap:      $maxGap,
                    convergedIn: $convergedIn,
                    createdAt:   $createdAt,
                    eigenvalues: $eigenvalues
                })
                WITH sr
                UNWIND $stringIds AS stringId
                MATCH (p:Protein {stringId: stringId})
                CREATE (sr)-[:COVERS]->(p)
                """)
                .bindAll(params)
                .run();
    }
}
