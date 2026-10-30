package com.proteinlens.graphcomputeservice.cli;

import com.proteinlens.graphcomputeservice.model.ProteinSubgraph;
import com.proteinlens.graphcomputeservice.model.SubgraphEdge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

/**
 * Reads a tab-separated edge list (source, target, score) into a {@link ProteinSubgraph}.
 * Files ending in .gz are decompressed. Blank lines, '#' comments and a non-numeric header row are skipped.
 *
 * Production loads both A→B and B→A from Neo4j and NormalizedLaplacianService keeps only i < j, so each
 * unordered pair is emitted in both directions here; duplicate pairs keep their highest score.
 */
public final class EdgeListReader {

    private EdgeListReader() {}

    public static ProteinSubgraph read(Path path) throws IOException {
        Map<String, Double> best = new HashMap<>();
        TreeSet<String> nodes = new TreeSet<>();

        try (InputStream raw = Files.newInputStream(path);
             InputStream in = path.toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw;
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split("\t");
                if (f.length < 3) {
                    throw new IllegalArgumentException(path + ":" + lineNo + ": expected 3 tab-separated fields");
                }
                double score;
                try {
                    score = Double.parseDouble(f[2].trim());
                } catch (NumberFormatException e) {
                    if (lineNo == 1) continue; // header row
                    throw new IllegalArgumentException(path + ":" + lineNo + ": score is not a number: " + f[2]);
                }
                String a = f[0].trim(), b = f[1].trim();
                nodes.add(a);
                nodes.add(b);
                if (a.equals(b)) continue; // self-loops are excluded from A
                String lo = a.compareTo(b) < 0 ? a : b, hi = a.compareTo(b) < 0 ? b : a;
                best.merge(lo + '\t' + hi, score, Math::max);
            }
        }

        List<SubgraphEdge> edges = new ArrayList<>(best.size() * 2);
        for (Map.Entry<String, Double> e : best.entrySet()) {
            String[] p = e.getKey().split("\t");
            edges.add(new SubgraphEdge(p[0], p[1], e.getValue()));
            edges.add(new SubgraphEdge(p[1], p[0], e.getValue()));
        }
        return new ProteinSubgraph(List.copyOf(nodes), edges);
    }
}
