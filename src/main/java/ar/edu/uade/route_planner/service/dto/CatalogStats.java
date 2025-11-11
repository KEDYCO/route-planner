package ar.edu.uade.route_planner.service.dto;

public record CatalogStats(
    int totalCountries,
    int totalCities,
    int totalStations,
    int totalAirports,
    int totalTrainStations
) {}

