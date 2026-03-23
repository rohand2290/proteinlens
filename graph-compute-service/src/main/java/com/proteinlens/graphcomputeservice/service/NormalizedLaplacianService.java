package com.proteinlens.graphcomputeservice.service;

import com.proteinlens.graphcomputeservice.model.NodeIndexMap;
import com.proteinlens.graphcomputeservice.model.ProteinSubgraph;
import com.proteinlens.graphcomputeservice.model.SubgraphEdge;
import lombok.extern.slf4j.Slf4j;
import org.ejml.data.DMatrixSparseCSC;
import org.ejml.data.DMatrixSparseTriplet;
import org.ejml.ops.DConvertMatrixStruct;
import org.ejml.sparse.csc.CommonOps_DSCC; // used for sparse add (L = I - A_norm)
import org.springframework.stereotype.Service;

/**
 * Computes the normalized symmetric Laplacian L_sym = I - D^{-1/2} * A * D^{-1/2}
 * for a protein interaction subgraph.
 *
 * A is the symmetrized weighted adjacency matrix. D is the diagonal degree matrix
 * where D[i][i] = sum of edge weights in row i of A. Isolated nodes (degree 0)
 * yield D^{-1/2}[i][i] = 0 to avoid division by zero.
 *
 * The returned matrix rows/columns are in lexicographic order matching
 * subgraph.nodeIds(), guaranteed by SubgraphRepository's ORDER BY p.stringId.
 * Use NodeIndexMap.from(subgraph.nodeIds()) to map row indices back to protein IDs.
 *
 * Note on deduplication: STRING-DB returns both A→B and B→A for each interaction,
 * and the ingestion-service stores both as directed Neo4j edges. The adjacency loop
 * processes only canonical pairs (i < j) to prevent double-counting weights.
 */
@Slf4j
@Service
public class NormalizedLaplacianService {

    public DMatrixSparseCSC compute(ProteinSubgraph subgraph) {
        NodeIndexMap map = NodeIndexMap.from(subgraph.nodeIds());
        int n = map.size();

        log.debug("Computing normalized Laplacian: nodes={}, edges={}", n, subgraph.edges().size());

        // --- Build symmetrized adjacency matrix (triplet format) ---
        // Only process canonical pairs (i < j) to deduplicate directed Neo4j edges.
        // addItem for both (i,j) and (j,i) to produce a symmetric matrix.
        DMatrixSparseTriplet aTriplet = new DMatrixSparseTriplet(n, n, subgraph.edges().size() * 2);
        for (SubgraphEdge edge : subgraph.edges()) {
            int i = map.indexOf(edge.source());
            int j = map.indexOf(edge.target());
            if (i >= j) {
                continue; // skip self-loops and the reverse direction of already-processed pairs
            }
            aTriplet.addItem(i, j, edge.score());
            aTriplet.addItem(j, i, edge.score());
        }

        // Convert to CSC — the explicit cast disambiguates the overloaded method signature
        DMatrixSparseCSC a = DConvertMatrixStruct.convert(aTriplet, (DMatrixSparseCSC) null);

        // --- Compute weighted degree vector d[i] = sum of row i in A ---
        // Iterate CSC internal arrays directly: O(nnz), no dense conversion needed.
        double[] d = new double[n];
        for (int col = 0; col < n; col++) {
            int colStart = a.col_idx[col];
            int colEnd   = a.col_idx[col + 1];
            for (int ptr = colStart; ptr < colEnd; ptr++) {
                d[a.nz_rows[ptr]] += a.nz_values[ptr];
            }
        }

        // --- Compute D^{-1/2} diagonal vector ---
        double[] dInvSqrt = new double[n];
        for (int i = 0; i < n; i++) {
            dInvSqrt[i] = d[i] > 0.0 ? 1.0 / Math.sqrt(d[i]) : 0.0;
        }

        // --- Compute A_norm = D^{-1/2} * A * D^{-1/2} ---
        // Scale each non-zero a[row,col] by dInvSqrt[row] * dInvSqrt[col] in one pass
        // over the CSC column arrays. Avoids CommonOps_DSCC.multRows/multColumns which
        // have an off-by-one bug in EJML 0.43.1 when the array length equals numRows/numCols.
        for (int col = 0; col < n; col++) {
            int idx0 = a.col_idx[col];
            int idx1 = a.col_idx[col + 1];
            for (int ptr = idx0; ptr < idx1; ptr++) {
                int row = a.nz_rows[ptr];
                a.nz_values[ptr] *= dInvSqrt[row] * dInvSqrt[col];
            }
        }
        DMatrixSparseCSC aNorm = a; // a is now D^{-1/2} * A * D^{-1/2}

        // --- Build sparse identity matrix ---
        DMatrixSparseTriplet iTriplet = new DMatrixSparseTriplet(n, n, n);
        for (int i = 0; i < n; i++) {
            iTriplet.addItem(i, i, 1.0);
        }
        DMatrixSparseCSC identity = DConvertMatrixStruct.convert(iTriplet, (DMatrixSparseCSC) null);

        // --- L_sym = I - A_norm ---
        // Null workspace args are valid; EJML allocates internal workspace as needed.
        DMatrixSparseCSC l = new DMatrixSparseCSC(n, n, identity.nz_length + aNorm.nz_length);
        CommonOps_DSCC.add(1.0, identity, -1.0, aNorm, l, null, null);

        log.debug("Normalized Laplacian computed: nnz={}", l.nz_length);

        return l;
    }
}
