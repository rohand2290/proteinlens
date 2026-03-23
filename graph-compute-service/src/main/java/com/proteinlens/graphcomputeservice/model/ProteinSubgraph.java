package com.proteinlens.graphcomputeservice.model;

import java.util.List;

/**
 * Immutable snapshot of a protein interaction subgraph loaded from Neo4j.
 *
 * {@code nodeIds} are ordered consistently so callers can build an adjacency matrix
 * by mapping each stringId to its list index.
 *
 * @param nodeIds ordered list of Protein stringIds present in the subgraph
 * @param edges   directed, weighted INTERACTS_WITH edges between those nodes
 */
public record ProteinSubgraph(List<String> nodeIds, List<SubgraphEdge> edges) {}
