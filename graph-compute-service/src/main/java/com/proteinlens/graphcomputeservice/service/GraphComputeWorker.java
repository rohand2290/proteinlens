package com.proteinlens.graphcomputeservice.service;

import com.proteinlens.graphcomputeservice.dto.ComputeJobDto;
import com.proteinlens.graphcomputeservice.model.NodeIndexMap;
import com.proteinlens.graphcomputeservice.model.ProteinSubgraph;
import com.proteinlens.graphcomputeservice.model.SpectralResult;
import com.proteinlens.graphcomputeservice.repository.SubgraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ejml.data.DMatrixSparseCSC;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the full spectral pipeline for a given compute job:
 *   1. Load protein subgraph from Neo4j
 *   2. Compute normalized symmetric Laplacian
 *   3. Run spectral analysis (eigendecomposition, clustering, centrality)
 *   4. Persist results back to Neo4j
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphComputeWorker {

    private final SubgraphRepository subgraphRepository;
    private final NormalizedLaplacianService normalizedLaplacianService;
    private final SpectralAnalyzerService spectralAnalyzerService;
    private final SpectralResultWriter spectralResultWriter;

    public void process(ComputeJobDto job) {
        log.info("Starting compute for job={}, proteins={}, interactions={}",
                job.getJobId(), job.getProteinsIngested(), job.getInteractionsIngested());

        try {
            // Step 1: ingest subgraph from Neo4j
            ProteinSubgraph subgraph = subgraphRepository.loadSubgraph(job.getIdentifiers());
            log.info("Loaded subgraph: nodes={}, edges={}", subgraph.nodeIds().size(), subgraph.edges().size());

            // Step 2: compute normalized Laplacian
            DMatrixSparseCSC laplacian = normalizedLaplacianService.compute(subgraph);

            // Step 3: spectral analysis
            SpectralResult result = spectralAnalyzerService.analyze(laplacian);
            log.info("Spectral analysis complete: kStar={}, maxGap={}, convergedIn={}",
                    result.kStar(), result.maxGap(), result.convergedIn());

            // Step 4: write results to Neo4j
            NodeIndexMap nodeIndexMap = NodeIndexMap.from(subgraph.nodeIds());
            spectralResultWriter.write(job, nodeIndexMap, result);

            log.info("Compute complete for job={}", job.getJobId());
        } catch (Exception e) {
            log.error("Compute failed for job={}: {}", job.getJobId(), e.getMessage(), e);
            // Re-throw to trigger Kafka retry/DLQ if configured
            throw e;
        }
    }
}
