package ar.edu.uade.route_planner.service.dto;

import java.util.List;

public record OptimalTripDto(
    String startCity,
    double budget,
    List<String> optimalRoute,
    double totalPrice,
    int citiesVisited,
    double totalDuration,
    double totalDistance,
    List<BacktrackSegment> segments,
    String message,
    double executionTimeMs,
    int nodesExplored,
    int nodesPruned
) {}

