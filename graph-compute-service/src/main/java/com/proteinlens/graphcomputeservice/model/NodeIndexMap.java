package com.proteinlens.graphcomputeservice.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable bidirectional mapping between a protein STRING ID and its integer
 * matrix index for adjacency / Laplacian matrix construction.
 *
 * The input list must already be lexicographically ordered — SubgraphRepository
 * guarantees this via ORDER BY p.stringId in its Cypher query.
 */
public final class NodeIndexMap {

    private final List<String> nodeIds;       // index → stringId
    private final Map<String, Integer> index; // stringId → index

    private NodeIndexMap(List<String> sortedNodeIds) {
        this.nodeIds = Collections.unmodifiableList(List.copyOf(sortedNodeIds));
        Map<String, Integer> idx = new HashMap<>(sortedNodeIds.size() * 2);
        for (int i = 0; i < sortedNodeIds.size(); i++) {
            idx.put(sortedNodeIds.get(i), i);
        }
        this.index = Collections.unmodifiableMap(idx);
    }

    public static NodeIndexMap from(List<String> sortedNodeIds) {
        return new NodeIndexMap(sortedNodeIds);
    }

    /**
     * Returns the matrix index for the given STRING stable ID.
     *
     * @throws IllegalArgumentException if the ID is not in this map — surfaces
     *                                  data-integrity problems loudly rather than
     *                                  silently producing -1 and corrupting the matrix
     */
    public int indexOf(String stringId) {
        Integer i = index.get(stringId);
        if (i == null) {
            throw new IllegalArgumentException("Unknown stringId: " + stringId);
        }
        return i;
    }

    /**
     * Returns the STRING stable ID at the given matrix index.
     *
     * @throws IndexOutOfBoundsException if idx is out of range
     */
    public String nodeIdAt(int idx) {
        return nodeIds.get(idx); // List.get() already throws IndexOutOfBoundsException
    }

    public int size() {
        return nodeIds.size();
    }
}
