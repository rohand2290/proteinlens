package com.proteinlens.graphcomputeservice.service;

/** Thrown when a graph exceeds a backend's size limit. Not retryable; routed straight to the DLQ. */
public class GraphTooLargeException extends RuntimeException {

    public GraphTooLargeException(int n, int maxN) {
        super("Graph has n=" + n + " nodes, exceeding the limit of " + maxN + " for this spectral backend");
    }
}
