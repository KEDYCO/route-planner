package ar.edu.uade.route_planner.service.dto;

import java.util.List;

public record PathDto(List<String> path, int hops, List<String> visited, double totalDistance) {}

