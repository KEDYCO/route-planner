package ar.edu.uade.route_planner.service;

import ar.edu.uade.route_planner.domain.Connection;
import ar.edu.uade.route_planner.domain.Station;
import ar.edu.uade.route_planner.repo.StationRepo;
import ar.edu.uade.route_planner.service.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════
 * ALGORITMO: BACKTRACKING (RETROCESO)
 * ═══════════════════════════════════════════════════════════════
 * Servicio para búsqueda exhaustiva de todas las soluciones posibles
 * 
 * ALGORITMO IMPLEMENTADO:
 * - BACKTRACKING (Retroceso) - Explora TODAS las posibilidades
 *   · Búsqueda exhaustiva sin poda
 *   · Encuentra todas las rutas posibles entre dos ciudades
 *   · Retrocede cuando alcanza un callejón sin salida
 *   · Más lento que Branch & Bound pero garantiza encontrar todas las soluciones
 * ═══════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
public class BacktrackingService {
    private final StationRepo repo;

    // ==================== BACKTRACKING: TODOS LOS CAMINOS ====================
    // ALGORITMO: BACKTRACKING - Encuentra TODAS las rutas posibles
    // Explora exhaustivamente todas las combinaciones sin poda
    public AllPathsDto findAllPaths(String fromCity, String toCity, Integer maxStops) {
        long startTime = System.nanoTime();
        System.out.println("🔍 Iniciando búsqueda de TODOS los caminos: " + fromCity + " → " + toCity);
        
        List<Station> fromStations = repo.findStationsByCity(fromCity);
        List<Station> toStations = repo.findStationsByCity(toCity);
        
        if (fromStations.isEmpty()) {
            return new AllPathsDto(
                fromCity, toCity, maxStops != null ? maxStops : 5,
                0, List.of(), null, null, null,
                "Ciudad de origen no encontrada: " + fromCity, 0.0, 0
            );
        }
        
        if (toStations.isEmpty()) {
            return new AllPathsDto(
                fromCity, toCity, maxStops != null ? maxStops : 5,
                0, List.of(), null, null, null,
                "Ciudad de destino no encontrada: " + toCity, 0.0, 0
            );
        }
        
        int maxStopsLimit = maxStops != null ? maxStops : 5;
        List<PathOption> allPaths = new ArrayList<>();
        int[] nodesExplored = {0};
        
        // Explorar desde todas las estaciones de origen hacia todas las de destino
        Set<String> targetCodes = new HashSet<>();
        for (Station s : toStations) {
            targetCodes.add(s.code);
        }
        
        for (Station startStation : fromStations) {
            List<String> currentPath = new ArrayList<>();
            List<TripSegment> currentSegments = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            
            backtrackSearch(
                startStation.code,
                targetCodes,
                currentPath,
                currentSegments,
                visited,
                0.0, 0.0, 0.0,
                maxStopsLimit,
                allPaths,
                nodesExplored
            );
        }
        
        long endTime = System.nanoTime();
        double executionTimeMs = (endTime - startTime) / 1_000_000.0;
        
        if (allPaths.isEmpty()) {
            return new AllPathsDto(
                fromCity, toCity, maxStopsLimit,
                0, List.of(), null, null, null,
                "No se encontraron rutas con máximo " + maxStopsLimit + " paradas",
                executionTimeMs, nodesExplored[0]
            );
        }
        
        // Ordenar por diferentes criterios para estadísticas
        PathOption cheapest = allPaths.stream()
            .min(Comparator.comparingDouble(PathOption::totalPrice))
            .orElse(null);
        
        PathOption fastest = allPaths.stream()
            .min(Comparator.comparingDouble(PathOption::totalDuration))
            .orElse(null);
        
        PathOption shortest = allPaths.stream()
            .min(Comparator.comparingDouble(PathOption::totalDistance))
            .orElse(null);
        
        // Ordenar todas las rutas por precio (por defecto)
        allPaths.sort(Comparator.comparingDouble(PathOption::totalPrice));
        
        String message = String.format(
            "Se encontraron %d rutas posibles. Más barata: €%.2f, Más rápida: %.2fh, Más corta: %.0fkm",
            allPaths.size(),
            cheapest != null ? cheapest.totalPrice() : 0,
            fastest != null ? fastest.totalDuration() : 0,
            shortest != null ? shortest.totalDistance() : 0
        );
        
        System.out.println("✅ Backtracking completado: " + allPaths.size() + " rutas encontradas");
        
        return new AllPathsDto(
            fromCity, toCity, maxStopsLimit,
            allPaths.size(), allPaths,
            cheapest, fastest, shortest,
            message, executionTimeMs, nodesExplored[0]
        );
    }
    
    // Función recursiva de backtracking - Explora TODAS las posibilidades
    private void backtrackSearch(
        String currentStation,
        Set<String> targetStations,
        List<String> currentPath,
        List<TripSegment> currentSegments,
        Set<String> visited,
        double currentPrice,
        double currentDuration,
        double currentDistance,
        int remainingStops,
        List<PathOption> solutions,
        int[] nodesExplored
    ) {
        nodesExplored[0]++;
        
        // Marcar como visitado
        visited.add(currentStation);
        currentPath.add(currentStation);
        
        // Si llegamos a una estación destino, guardar solución
        if (targetStations.contains(currentStation) && currentPath.size() > 1) {
            PathOption solution = new PathOption(
                new ArrayList<>(currentPath),
                currentPath.size() - 1,
                currentPrice,
                currentDuration,
                currentDistance,
                new ArrayList<>(currentSegments)
            );
            solutions.add(solution);
            
            if (solutions.size() % 50 == 0) {
                System.out.println("  📊 Rutas encontradas hasta ahora: " + solutions.size());
            }
        }
        
        // Si aún podemos hacer más paradas, explorar vecinos
        if (remainingStops > 0) {
            Station hop = repo.oneHop(currentStation);
            
            if (hop != null && hop.edges != null) {
                for (Connection conn : hop.edges) {
                    String nextStation = conn.to.code;
                    
                    // BACKTRACKING: Explorar incluso si ya visitamos (permite encontrar más caminos)
                    // Solo evitamos ciclos inmediatos
                    if (!visited.contains(nextStation)) {
                        
                        // Agregar segmento
                        TripSegment segment = new TripSegment(
                            currentStation,
                            hop.name,
                            nextStation,
                            conn.to.name,
                            conn.mode,
                            conn.carrier,
                            conn.price,
                            conn.duration,
                            conn.dist
                        );
                        currentSegments.add(segment);
                        
                        // Recursión
                        backtrackSearch(
                            nextStation,
                            targetStations,
                            currentPath,
                            currentSegments,
                            visited,
                            currentPrice + (conn.price != null ? conn.price : 0),
                            currentDuration + (conn.duration != null ? conn.duration : 0),
                            currentDistance + (conn.dist != null ? conn.dist : 0),
                            remainingStops - 1,
                            solutions,
                            nodesExplored
                        );
                        
                        // BACKTRACK: Deshacer cambios
                        currentSegments.remove(currentSegments.size() - 1);
                    }
                }
            }
        }
        
        // BACKTRACK: Desmarcar como visitado al retroceder
        currentPath.remove(currentPath.size() - 1);
        visited.remove(currentStation);
    }
}

