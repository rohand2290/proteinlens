package com.proteinlens.graphcomputeservice.cli;

import com.proteinlens.graphcomputeservice.GraphComputeServiceApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpectralCliTest {

    @Test
    void clustersTwoCliquesFromGzippedEdgeList(@TempDir Path dir) throws Exception {
        Path in = dir.resolve("edges.tsv.gz");
        try (Writer w = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(in)))) {
            w.write("source\ttarget\tscore\n");
            String[][] cliques = {{"A1", "A2", "A3", "A4"}, {"B1", "B2", "B3", "B4"}};
            for (String[] c : cliques)
                for (int i = 0; i < c.length; i++)
                    for (int j = i + 1; j < c.length; j++) w.write(c[i] + "\t" + c[j] + "\t0.9\n");
            w.write("A1\tB1\t0.1\n");
        }
        Path out = dir.resolve("out/clusters.csv");

        SpringApplication app = new SpringApplication(GraphComputeServiceApplication.class);
        app.setAdditionalProfiles("cli");
        app.run(in.toString(), out.toString(), "2").close();

        List<String> lines = Files.readAllLines(out);
        assertEquals("stringId,clusterId,centrality", lines.get(0));
        assertEquals(9, lines.size());
        String a = lines.get(1).split(",")[1], b = lines.get(5).split(",")[1];
        for (int i = 1; i <= 4; i++) assertEquals(a, lines.get(i).split(",")[1]);
        for (int i = 5; i <= 8; i++) assertEquals(b, lines.get(i).split(",")[1]);
        org.junit.jupiter.api.Assertions.assertNotEquals(a, b);
    }
}
