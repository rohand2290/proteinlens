package com.proteinlens.graphcomputeservice.cli;

import com.proteinlens.graphcomputeservice.model.SpectralResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes one row per protein: stringId, clusterId, centrality. */
public final class ClusterCsvWriter {

    private ClusterCsvWriter() {}

    public static void write(Path out, List<String> nodeIds, SpectralResult r) throws IOException {
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("stringId,clusterId,centrality\n");
            for (int i = 0; i < nodeIds.size(); i++) {
                w.write(nodeIds.get(i) + "," + r.clusterAssignments()[i] + "," + r.centrality()[i] + "\n");
            }
        }
    }
}
