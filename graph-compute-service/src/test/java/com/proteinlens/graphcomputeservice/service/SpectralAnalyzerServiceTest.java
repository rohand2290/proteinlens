package com.proteinlens.graphcomputeservice.service;

import com.proteinlens.graphcomputeservice.model.GraphMatrices;
import com.proteinlens.graphcomputeservice.model.ProteinSubgraph;
import com.proteinlens.graphcomputeservice.model.SpectralResult;
import com.proteinlens.graphcomputeservice.model.SubgraphEdge;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpectralAnalyzerServiceTest {

    private final NormalizedLaplacianService laplacian = new NormalizedLaplacianService();
    private final SpectralAnalyzerService analyzer = new SpectralAnalyzerService(new DenseEjmlBackend());

    private static ProteinSubgraph graph(int n, int[][] edges) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) ids.add(String.format("P%02d", i));
        List<SubgraphEdge> es = new ArrayList<>();
        for (int[] e : edges) es.add(new SubgraphEdge(ids.get(e[0]), ids.get(e[1]), 1.0));
        return new ProteinSubgraph(ids, es);
    }

    @Test
    void starHubRanksFirst() {
        GraphMatrices m = laplacian.compute(graph(6, new int[][]{{0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5}}));
        SpectralResult r = analyzer.analyze(m);
        assertEquals(1.0, r.centrality()[0], 1e-12);
        for (int i = 1; i < 6; i++) assertTrue(r.centrality()[i] < 1.0);
    }

    @Test
    void twoComponentsHaveNoNaNAndPageRankSumsToOne() {
        GraphMatrices m = laplacian.compute(graph(6, new int[][]{{0, 1}, {1, 2}, {0, 2}, {3, 4}, {4, 5}, {3, 5}}));
        int[] iters = new int[1];
        double[] pr = SpectralAnalyzerService.pageRank(m.adjacency(), m.degree(), 0.85, 1e-12, 500, iters);
        double sum = 0;
        for (double v : pr) { assertFalse(Double.isNaN(v)); sum += v; }
        assertEquals(1.0, sum, 1e-9);
        SpectralResult r = analyzer.analyze(m);
        for (double c : r.centrality()) assertFalse(Double.isNaN(c));
        assertEquals(2, r.kStar()); // two components -> at least 2 clusters
    }

    @Test
    void isolatedNodeDoesNotBreakPageRank() {
        GraphMatrices m = laplacian.compute(graph(4, new int[][]{{0, 1}, {1, 2}}));
        int[] iters = new int[1];
        double[] pr = SpectralAnalyzerService.pageRank(m.adjacency(), m.degree(), 0.85, 1e-12, 500, iters);
        double sum = 0;
        for (double v : pr) { assertFalse(Double.isNaN(v)); sum += v; }
        assertEquals(1.0, sum, 1e-9);
    }

    @Test
    void adjacencyIsPreservedAfterLaplacian() {
        GraphMatrices m = laplacian.compute(graph(3, new int[][]{{0, 1}, {1, 2}}));
        assertEquals(1.0, m.adjacency().get(0, 1), 1e-12);
        assertEquals(2.0, m.degree()[1], 1e-12);
    }

    @Test
    void chooseKStaysWithinBounds() {
        // eigenvalues of 2 components (two zeros), biggest gap at the very end
        double[] eig = {0, 0, 0.1, 0.2, 0.3, 1.9};
        assertEquals(2, SpectralAnalyzerService.chooseK(eig, 4));      // gap at k=5 is out of range
        assertEquals(5, SpectralAnalyzerService.chooseK(eig, 20));     // hi = n-1 = 5
        assertEquals(1, SpectralAnalyzerService.chooseK(new double[]{0, 0.9, 1.0}, 1));
    }

    @Test
    void kOverrideIsHonored() {
        GraphMatrices m = laplacian.compute(graph(6, new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}}));
        assertEquals(3, analyzer.analyze(m, 3).kStar());
    }

    @Test
    void moreComponentsThanKMaxStillGetOneClusterEach() {
        // 30 disjoint edges = 30 components, above the default kMax of 20
        int[][] edges = new int[30][];
        for (int i = 0; i < 30; i++) edges[i] = new int[]{2 * i, 2 * i + 1};
        GraphMatrices m = laplacian.compute(graph(60, edges));
        assertEquals(30, SpectralAnalyzerService.connectedComponents(m.adjacency()));
        assertEquals(30, analyzer.analyze(m).kStar());
    }
}
