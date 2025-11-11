package ar.edu.uade.route_planner.service.dto;

public record StationInfo(
    String code,
    String name,
    String type,
    String cityCode,
    String cityName,
    String country,
    Integer connectionCount
) {}

