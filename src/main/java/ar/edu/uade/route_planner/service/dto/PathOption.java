package ar.edu.uade.route_planner.service.dto;

import java.util.List;

public record PathOption(
    List<String> path,
    int stops,
    double totalPrice,
    double totalDuration,
    double totalDistance,
    List<TripSegment> segments
) {}

