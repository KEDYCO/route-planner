package ar.edu.uade.route_planner.service.dto;

import java.util.List;

public record MultiCityTourDto(
    List<String> tourOrder,
    int citiesVisited,
    Double totalPrice,
    Double totalDuration,
    Double totalDistance,
    List<TourSegment> segments,
    String message,
    double executionTimeMs
) {}

