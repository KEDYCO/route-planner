package ar.edu.uade.route_planner.service.dto;

import java.util.List;

public record CountryData(
    String country,
    List<String> cities,
    List<StationInfo> airports,
    List<StationInfo> trainStations
) {}

