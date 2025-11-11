package ar.edu.uade.route_planner.service.dto;

import java.util.List;

public record CatalogDto(
    CatalogStats stats,
    List<CountryData> byCountry,
    List<StationInfo> topAirports,
    String sortingAlgorithm,
    double sortingTimeMs
) {}

