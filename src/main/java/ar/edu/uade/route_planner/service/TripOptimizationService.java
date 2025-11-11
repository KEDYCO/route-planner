package ar.edu.uade.route_planner.service;

import ar.edu.uade.route_planner.domain.Station;
import ar.edu.uade.route_planner.repo.StationRepo;
import ar.edu.uade.route_planner.service.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════
 * ALGORITMO: BRANCH & BOUND (RAMIFICACIÓN Y PODA)
 * ═══════════════════════════════════════════════════════════════
 * Servicio para optimización de viajes
 * 
 * ALGORITMO IMPLEMENTADO:
 * - BRANCH & BOUND (Ramificación y Poda)
 *   · Explora árbol de decisiones con búsqueda exhaustiva
 *   · PODA ramas que no pueden dar mejor solución (bound optimista)
 *   · Maximiza ciudades visitadas dentro de presupuesto
 *   · Más eficiente que backtracking puro
 * ═══════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
public class TripOptimizationService {
    private final StationRepo repo;
    private final Neo4jClient neo4jClient;
    private final GreedyPathfindingService greedyService;

    // ==================== BRANCH & BOUND ====================
    // ALGORITMO: BRANCH & BOUND - Ramificación y poda para optimización
    // Maximiza ciudades visitadas podando ramas no prometedoras
    public OptimalTripDto optimizeTrip(String startCity, double budget, 
                                       Integer maxCities, Boolean returnToOrigin) {
        long startTime = System.nanoTime();
        System.out.println("🎯 Optimizando viaje desde " + startCity + " con presupuesto €" + budget);
        
        List<Station> startStations = repo.findStationsByCity(startCity);
        if (startStations.isEmpty()) {
            return new OptimalTripDto(
                startCity, budget, null, 0.0, 0, 0.0, 0.0, null,
                "Ciudad no encontrada: " + startCity, 0.0, 0, 0
            );
        }
        
        List<String> allCities = neo4jClient.query("""
            MATCH (c:City)
            RETURN DISTINCT c.code as cityCode
            ORDER BY c.code
            """)
            .fetch()
            .all()
            .stream()
            .map(record -> record.get("cityCode").toString())
            .toList();
        
        System.out.println("📍 Ciudades disponibles: " + allCities.size());
        
        Map<String, Map<String, GreedyPathDto>> cityConnectionCache = new HashMap<>();
        double cheapestConnection = Double.MAX_VALUE;
        
        for (String city1 : allCities) {
            cityConnectionCache.put(city1, new HashMap<>());
            for (String city2 : allCities) {
                if (!city1.equals(city2)) {
                    GreedyPathDto path = greedyService.greedyPathByCity(city1, city2, "price", null, null, null);
                    if (!path.path().isEmpty() && path.totalPrice() != null) {
                        cityConnectionCache.get(city1).put(city2, path);
                        cheapestConnection = Math.min(cheapestConnection, path.totalPrice());
                    }
                }
            }
        }
        
        System.out.println("✅ Rutas pre-calculadas. Conexión más barata: €" + cheapestConnection);
        
        int maxCitiesLimit = maxCities != null ? maxCities : Math.min(10, allCities.size());
        boolean mustReturn = returnToOrigin != null && returnToOrigin;
        
        OptimalTripState bestSolution = new OptimalTripState();
        bestSolution.score = Double.NEGATIVE_INFINITY;
        
        int[] nodesExplored = {0};
        int[] nodesPruned = {0};
        
        Set<String> visited = new LinkedHashSet<>();
        List<String> currentPath = new ArrayList<>();
        visited.add(startCity);
        currentPath.add(startCity);
        
        optimizeTripSearch(
            startCity, startCity, currentPath, visited,
            0.0, 0.0, 0.0, budget, maxCitiesLimit, mustReturn,
            cheapestConnection, cityConnectionCache,
            bestSolution, nodesExplored, nodesPruned, new ArrayList<>()
        );
        
        long endTime = System.nanoTime();
        double executionTimeMs = (endTime - startTime) / 1_000_000.0;
        
        if (bestSolution.path == null || bestSolution.path.isEmpty()) {
            return new OptimalTripDto(
                startCity, budget, null, 0.0, 0, 0.0, 0.0, null,
                "No se encontró ruta dentro del presupuesto", executionTimeMs,
                nodesExplored[0], nodesPruned[0]
            );
        }
        
        return new OptimalTripDto(
            startCity, budget,
            bestSolution.path, bestSolution.totalPrice, bestSolution.citiesVisited,
            bestSolution.totalDuration, bestSolution.totalDistance,
            bestSolution.segments,
            String.format("Ruta óptima: %d ciudades visitadas, €%.2f gastados",
                bestSolution.citiesVisited, bestSolution.totalPrice),
            executionTimeMs, nodesExplored[0], nodesPruned[0]
        );
    }
    
    // Función recursiva de Branch & Bound con poda optimista
    private void optimizeTripSearch(
        String startCity, String currentCity, List<String> currentPath, Set<String> visited,
        double currentPrice, double currentDuration, double currentDistance,
        double budget, int maxCities, boolean returnToOrigin,
        double cheapestConnection,
        Map<String, Map<String, GreedyPathDto>> cityConnectionCache,
        OptimalTripState bestSolution, int[] nodesExplored, int[] nodesPruned,
        List<BacktrackSegment> currentSegments) {
        
        nodesExplored[0]++;
        
        int currentScore = currentPath.size();
        
        if (currentPath.size() > 1) {
            boolean isValidSolution = !returnToOrigin || currentCity.equals(startCity);
            
            if (isValidSolution && currentScore > bestSolution.score) {
                bestSolution.path = new ArrayList<>(currentPath);
                bestSolution.citiesVisited = currentPath.size();
                bestSolution.totalPrice = currentPrice;
                bestSolution.totalDuration = currentDuration;
                bestSolution.totalDistance = currentDistance;
                bestSolution.score = currentScore;
                bestSolution.segments = new ArrayList<>(currentSegments);
                
                if (nodesExplored[0] % 500 == 0) {
                    System.out.println("  🏆 Nueva mejor solución: " + currentPath.size() + " ciudades, €" + 
                                      String.format("%.2f", currentPrice));
                }
            }
        }
        
        if (currentPath.size() >= maxCities) {
            if (returnToOrigin && !currentCity.equals(startCity)) {
                tryReturnToOrigin(startCity, currentCity, currentPath, currentPrice, currentDuration,
                    currentDistance, budget, cityConnectionCache, bestSolution, currentSegments);
            }
            return;
        }
        
        double remainingBudget = budget - currentPrice;
        int maxPossibleCities = Math.min(
            currentPath.size() + (int)(remainingBudget / cheapestConnection),
            maxCities
        );
        
        if (maxPossibleCities <= bestSolution.score) {
            nodesPruned[0]++;
            return;
        }
        
        Map<String, GreedyPathDto> reachableCities = cityConnectionCache.get(currentCity);
        if (reachableCities == null || reachableCities.isEmpty()) {
            return;
        }
        
        List<Map.Entry<String, GreedyPathDto>> sortedCities = new ArrayList<>(reachableCities.entrySet());
        sortedCities.sort(Comparator.comparingDouble(e -> e.getValue().totalPrice()));
        
        for (Map.Entry<String, GreedyPathDto> entry : sortedCities) {
            String nextCity = entry.getKey();
            GreedyPathDto pathToNext = entry.getValue();
            
            if (visited.contains(nextCity) && !(returnToOrigin && nextCity.equals(startCity) && currentPath.size() > 2)) {
                continue;
            }
            
            double newPrice = currentPrice + pathToNext.totalPrice();
            double newDuration = currentDuration + (pathToNext.totalDuration() != null ? pathToNext.totalDuration() : 0);
            double newDistance = currentDistance + (pathToNext.totalDistance() != null ? pathToNext.totalDistance() : 0);
            
            if (newPrice > budget) {
                nodesPruned[0]++;
                continue;
            }
            
            if (nextCity.equals(startCity) && returnToOrigin && currentPath.size() >= 2) {
                int returnScore = currentPath.size();
                if (returnScore > bestSolution.score) {
                    bestSolution.path = new ArrayList<>(currentPath);
                    bestSolution.path.add(nextCity);
                    bestSolution.citiesVisited = currentPath.size();
                    bestSolution.totalPrice = newPrice;
                    bestSolution.totalDuration = newDuration;
                    bestSolution.totalDistance = newDistance;
                    bestSolution.score = returnScore;
                    
                    List<BacktrackSegment> newSegments = new ArrayList<>(currentSegments);
                    newSegments.add(new BacktrackSegment(currentCity, nextCity, pathToNext.segments(), pathToNext.totalPrice()));
                    bestSolution.segments = newSegments;
                }
                continue;
            }
            
            visited.add(nextCity);
            currentPath.add(nextCity);
            List<BacktrackSegment> newSegments = new ArrayList<>(currentSegments);
            newSegments.add(new BacktrackSegment(currentCity, nextCity, pathToNext.segments(), pathToNext.totalPrice()));
            
            optimizeTripSearch(
                startCity, nextCity, currentPath, visited,
                newPrice, newDuration, newDistance, budget, maxCities, returnToOrigin,
                cheapestConnection, cityConnectionCache,
                bestSolution, nodesExplored, nodesPruned, newSegments
            );
            
            currentPath.remove(currentPath.size() - 1);
            visited.remove(nextCity);
        }
    }
    
    private void tryReturnToOrigin(
        String startCity, String currentCity, List<String> currentPath,
        double currentPrice, double currentDuration, double currentDistance,
        double budget,
        Map<String, Map<String, GreedyPathDto>> cityConnectionCache,
        OptimalTripState bestSolution, List<BacktrackSegment> currentSegments) {
        
        GreedyPathDto returnPath = cityConnectionCache.get(currentCity) != null ?
            cityConnectionCache.get(currentCity).get(startCity) : null;
        
        if (returnPath == null) return;
        
        double totalPrice = currentPrice + returnPath.totalPrice();
        double totalDuration = currentDuration + (returnPath.totalDuration() != null ? returnPath.totalDuration() : 0);
        double totalDistance = currentDistance + (returnPath.totalDistance() != null ? returnPath.totalDistance() : 0);
        
        if (totalPrice <= budget) {
            int score = currentPath.size();
            
            if (score > bestSolution.score) {
                bestSolution.path = new ArrayList<>(currentPath);
                bestSolution.path.add(startCity);
                bestSolution.citiesVisited = currentPath.size();
                bestSolution.totalPrice = totalPrice;
                bestSolution.totalDuration = totalDuration;
                bestSolution.totalDistance = totalDistance;
                bestSolution.score = score;
                
                List<BacktrackSegment> newSegments = new ArrayList<>(currentSegments);
                newSegments.add(new BacktrackSegment(currentCity, startCity, returnPath.segments(), returnPath.totalPrice()));
                bestSolution.segments = newSegments;
            }
        }
    }
    
    private static class OptimalTripState {
        List<String> path;
        int citiesVisited;
        double totalPrice;
        double totalDuration;
        double totalDistance;
        double score;
        List<BacktrackSegment> segments;
    }
}

