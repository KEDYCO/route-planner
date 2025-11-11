package ar.edu.uade.route_planner.service.dto;

import java.util.List;

public record GreedyPathDto(
    List<String> path,           // Lista de códigos de estaciones
    int hops,                    // Número de saltos
    List<String> visited,        // Nodos visitados durante la búsqueda
    List<TripSegment> segments,  // Información detallada de cada tramo
    Double totalPrice,           // Precio total real
    Double totalDuration,        // Duración total real
    Double totalDistance,        // Distancia total real
    String criterion             // Criterio usado
) {}

