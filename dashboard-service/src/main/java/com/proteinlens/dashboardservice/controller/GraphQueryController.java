package com.proteinlens.dashboardservice.controller;

import com.proteinlens.dashboardservice.dto.GraphResponseDto;
import com.proteinlens.dashboardservice.dto.GraphResponseDto.EdgeDto;
import com.proteinlens.dashboardservice.dto.GraphResponseDto.NodeDto;
import com.proteinlens.dashboardservice.dto.SpectralResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GraphQueryController {

    private final Neo4jClient neo4jClient;

    /**
     * Returns all protein nodes and interaction edges covered by a spectral result job.
     */
    @GetMapping("/graph/{jobId}")
    public ResponseEntity<GraphResponseDto> graph(@PathVariable String jobId) {
        List<NodeDto> nodes = neo4jClient.query("""
                MATCH (sr:SpectralResult {jobId: $jobId})-[:COVERS]->(p:Protein)
                RETURN p.stringId                                      AS id,
                       coalesce(p.preferredName, p.stringId)          AS label,
                       coalesce(p.clusterId, 0)                        AS clusterId,
                       coalesce(p.centralityScore, 0.0)               AS centrality
                """)
                .bind(jobId).to("jobId")
                .fetchAs(NodeDto.class)
                .mappedBy((ts, r) -> new NodeDto(
                        r.get("id").asString(),
                        r.get("label").asString(),
                        r.get("clusterId").asInt(0),
                        r.get("centrality").asDouble(0.0)
                ))
                .all()
                .stream().toList();

        List<EdgeDto> edges = neo4jClient.query("""
                MATCH (sr:SpectralResult {jobId: $jobId})-[:COVERS]->(a:Protein)
                MATCH (sr)-[:COVERS]->(b:Protein)
                MATCH (a)-[r:INTERACTS_WITH]->(b)
                RETURN a.stringId AS source, b.stringId AS target, r.score AS score
                """)
                .bind(jobId).to("jobId")
                .fetchAs(EdgeDto.class)
                .mappedBy((ts, r) -> new EdgeDto(
                        r.get("source").asString(),
                        r.get("target").asString(),
                        r.get("score").asDouble(0.0)
                ))
                .all()
                .stream().toList();

        return ResponseEntity.ok(new GraphResponseDto(nodes, edges));
    }

    /**
     * Returns eigenvalue spectrum and spectral gap metadata for a job.
     */
    @GetMapping("/spectral/{jobId}")
    public ResponseEntity<SpectralResponseDto> spectral(@PathVariable String jobId) {
        return neo4jClient.query("""
                MATCH (sr:SpectralResult {jobId: $jobId})
                RETURN sr.eigenvalues AS eigenvalues,
                       sr.kStar       AS kStar,
                       sr.maxGap      AS maxGap,
                       sr.convergedIn AS convergedIn
                """)
                .bind(jobId).to("jobId")
                .fetchAs(SpectralResponseDto.class)
                .mappedBy((ts, r) -> {
                    List<Double> eigenvalues = r.get("eigenvalues").asList(v -> v.asDouble());
                    int kStar = r.get("kStar").asInt();
                    double maxGap = r.get("maxGap").asDouble();
                    int convergedIn = r.get("convergedIn").asInt(0);
                    return new SpectralResponseDto(eigenvalues, kStar, maxGap, kStar - 1, convergedIn);
                })
                .one()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
