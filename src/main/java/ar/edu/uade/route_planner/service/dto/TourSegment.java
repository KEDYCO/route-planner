package ar.edu.uade.route_planner.service.dto;

import java.util.List;

public record TourSegment(
    String fromCity,
    String toCity,
    List<TripSegment> connections,
    double segmentCost
) {}

