package ar.edu.uade.route_planner.service.dto;

public record TripSegment(
    String from,           // Código de origen
    String fromName,       // Nombre de origen
    String to,             // Código de destino
    String toName,         // Nombre de destino
    String mode,           // Modo de transporte (air, train, bus)
    String carrier,        // Compañía (Vueling, RENFE, etc.)
    Double price,          // Precio del segmento
    Double duration,       // Duración del segmento
    Double distance        // Distancia del segmento
) {}

