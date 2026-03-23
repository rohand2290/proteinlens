package com.proteinlens.dashboardservice.dto;

import java.util.List;

public record GraphResponseDto(List<NodeDto> nodes, List<EdgeDto> edges) {

    public record NodeDto(String id, String label, int clusterId, double centrality) {}

    public record EdgeDto(String source, String target, double score) {}
}
