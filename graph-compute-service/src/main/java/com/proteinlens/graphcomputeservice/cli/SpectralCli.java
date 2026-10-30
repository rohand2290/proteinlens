package com.proteinlens.graphcomputeservice.cli;

import com.proteinlens.graphcomputeservice.model.GraphMatrices;
import com.proteinlens.graphcomputeservice.model.ProteinSubgraph;
import com.proteinlens.graphcomputeservice.model.SpectralResult;
import com.proteinlens.graphcomputeservice.service.NormalizedLaplacianService;
import com.proteinlens.graphcomputeservice.service.SpectralAnalyzerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * Runs the production spectral pipeline on an edge-list file so CI can evaluate the exact code the
 * service runs. Usage: {@code --spring.profiles.active=cli <edges.tsv[.gz]> <clusters.csv> [k]}
 */
@Slf4j
@Component
@Profile("cli")
@RequiredArgsConstructor
class SpectralCli implements CommandLineRunner {

    private final NormalizedLaplacianService laplacian;
    private final SpectralAnalyzerService analyzer;

    @Override
    public void run(String... args) throws Exception {
        String[] pos = Arrays.stream(args).filter(a -> !a.startsWith("--")).toArray(String[]::new);
        if (pos.length < 2) {
            throw new IllegalArgumentException("usage: <edges.tsv[.gz]> <clusters.csv> [k]");
        }
        ProteinSubgraph g = EdgeListReader.read(Path.of(pos[0]));
        Integer k = pos.length > 2 ? Integer.valueOf(pos[2]) : null;

        GraphMatrices matrices = laplacian.compute(g);
        SpectralResult r = analyzer.analyze(matrices, k);
        ClusterCsvWriter.write(Path.of(pos[1]), g.nodeIds(), r);
        log.info("CLI complete: nodes={} edges={} kStar={} maxGap={}",
                g.nodeIds().size(), g.edges().size() / 2, r.kStar(), r.maxGap());
    }
}
