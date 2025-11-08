package ar.edu.uade.route_planner.repo;

import java.util.Map;

public record EdgeRecord(String from, String to, Map<String, Object> edge) {
    // No necesita métodos adicionales
    // Los records ya tienen getters automáticos: from(), to(), edge()
}

