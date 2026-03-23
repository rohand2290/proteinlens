package com.proteinlens.graphcomputeservice.model;

/**
 * A directed, weighted edge in the protein interaction subgraph.
 *
 * @param source STRING stable ID of the source protein
 * @param target STRING stable ID of the target protein
 * @param score  combined interaction score from STRING-DB (0.0–1.0)
 */
public record SubgraphEdge(String source, String target, double score) {}
