package ar.edu.uade.route_planner.service.dto;

import java.util.List;

public record MSTDto(List<MSTEdge> edges, double totalWeight, int nodeCount, String message) {}

