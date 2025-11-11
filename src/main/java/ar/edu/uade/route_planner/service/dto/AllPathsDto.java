package ar.edu.uade.route_planner.service.dto;

import java.util.List;

public record AllPathsDto(
    String fromCity,
    String toCity,
    int maxStops,
    int totalPathsFound,
    List<PathOption> paths,
    PathOption cheapestPath,
    PathOption fastestPath,
    PathOption shortestPath,
    String message,
    double executionTimeMs,
    int nodesExplored
) {}

