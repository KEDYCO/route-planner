package ar.edu.uade.route_planner.service;

import ar.edu.uade.route_planner.domain.Station;
import ar.edu.uade.route_planner.repo.StationRepo;
import ar.edu.uade.route_planner.service.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════
 * ALGORITMO: PROGRAMACIÓN DINÁMICA (DYNAMIC PROGRAMMING)
 * ═══════════════════════════════════════════════════════════════
 * Servicio para planificación de tours multi-ciudad
 * 
 * ALGORITMO IMPLEMENTADO:
 * - TSP (Traveling Salesman Problem) con Programación Dinámica
 *   · Usa Bitmask DP para almacenar estados visitados
 *   · Encuentra el orden óptimo para visitar ciudades
 *   · Evita recalcular subproblemas (memoización)
 * ═══════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
public class TourPlannerService {
    private final StationRepo repo;
    private final GreedyPathfindingService greedyService;

    // ==================== TSP CON PROGRAMACIÓN DINÁMICA ====================
    // ALGORITMO: DYNAMIC PROGRAMMING - TSP con Bitmask
    // Encuentra el orden óptimo de visitar ciudades usando DP con máscaras de bits
    public MultiCityTourDto planMultiCityTour(String startCity, List<String> citiesToVisit, String criterion) {
        long startTime = System.nanoTime();
        System.out.println("\n═══════════════════════════════════════════════════════════════");
        System.out.println("🚀 INICIANDO ALGORITMO TSP (PROGRAMACIÓN DINÁMICA)");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("📍 Ciudad origen: " + startCity);
        System.out.println("🎯 Ciudades a visitar: " + citiesToVisit);
        System.out.println("⚙️  Criterio de optimización: " + (criterion != null ? criterion : "price"));
        
        // FASE 1: VALIDACIÓN DE CIUDADES
        System.out.println("\n--- FASE 1: Validación de Ciudades ---");
        Map<String, List<Station>> cityStations = new HashMap<>();
        cityStations.put(startCity, repo.findStationsByCity(startCity));
        
        for (String city : citiesToVisit) {
            cityStations.put(city, repo.findStationsByCity(city));
        }
        
        for (Map.Entry<String, List<Station>> entry : cityStations.entrySet()) {
            if (entry.getValue().isEmpty()) {
                System.out.println("❌ ERROR: Ciudad no encontrada - " + entry.getKey());
                return new MultiCityTourDto(
                    List.of(), 0, null, null, null, List.of(), 
                    "Ciudad no encontrada: " + entry.getKey(), 0.0
                );
            }
            System.out.println("✅ " + entry.getKey() + " - " + entry.getValue().size() + " estación(es) encontrada(s)");
        }
        
        List<String> allCities = new ArrayList<>();
        allCities.add(startCity);
        allCities.addAll(citiesToVisit);
        
        // FASE 2: CONSTRUCCIÓN DE MATRIZ DE COSTOS
        System.out.println("\n--- FASE 2: Construcción de Matriz de Costos ---");
        int n = allCities.size();
        System.out.println("🔢 Total de ciudades: " + n);
        double[][] costMatrix = new double[n][n];
        Map<String, GreedyPathDto> pathCache = new HashMap<>();
        
        System.out.println("📊 Calculando costos entre todas las ciudades...");
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    costMatrix[i][j] = 0;
                } else {
                    String cacheKey = allCities.get(i) + "→" + allCities.get(j);
                    
                    GreedyPathDto bestPath = greedyService.greedyPathByCity(
                        allCities.get(i), allCities.get(j), 
                        criterion != null ? criterion : "price",
                        null, null, null
                    );
                    
                    pathCache.put(cacheKey, bestPath);
                    
                    if (bestPath.path().isEmpty()) {
                        costMatrix[i][j] = Double.POSITIVE_INFINITY;
                        System.out.println("  ⚠️  " + allCities.get(i) + " → " + allCities.get(j) + ": SIN CONEXIÓN");
                    } else {
                        costMatrix[i][j] = getCostFromPath(bestPath, criterion);
                        System.out.println("  ✓ " + allCities.get(i) + " → " + allCities.get(j) + 
                            String.format(": costo %.2f (precio: %.2f, duración: %.2f, distancia: %.0f)", 
                            costMatrix[i][j],
                            bestPath.totalPrice() != null ? bestPath.totalPrice() : 0,
                            bestPath.totalDuration() != null ? bestPath.totalDuration() : 0,
                            bestPath.totalDistance() != null ? bestPath.totalDistance() : 0));
                    }
                }
            }
        }
        
        // FASE 3: INICIALIZACIÓN DEL DP
        System.out.println("\n--- FASE 3: Inicialización de Programación Dinámica ---");
        int citiesToVisitCount = citiesToVisit.size();
        int totalStates = 1 << citiesToVisitCount;
        System.out.println("🧮 Ciudades a visitar (sin contar origen): " + citiesToVisitCount);
        System.out.println("🔢 Estados posibles (2^n): " + totalStates);
        
        double[][] dp = new double[totalStates][citiesToVisitCount];
        int[][] parent = new int[totalStates][citiesToVisitCount];
        
        // Inicializar con infinito
        for (int mask = 0; mask < totalStates; mask++) {
            for (int i = 0; i < citiesToVisitCount; i++) {
                dp[mask][i] = Double.POSITIVE_INFINITY;
                parent[mask][i] = -1;
            }
        }
        
        // Estados base: desde origen a cada ciudad
        System.out.println("📍 Estados base (desde origen):");
        for (int i = 0; i < citiesToVisitCount; i++) {
            dp[1 << i][i] = costMatrix[0][i + 1];
            System.out.println("  Estado [" + (1 << i) + "][" + i + "]: " + startCity + " → " + 
                citiesToVisit.get(i) + " = " + String.format("%.2f", dp[1 << i][i]));
        }
        
        // FASE 4: ALGORITMO DE PROGRAMACIÓN DINÁMICA
        System.out.println("\n--- FASE 4: Ejecutando Programación Dinámica (TSP) ---");
        System.out.println("🔄 Calculando rutas óptimas para todas las combinaciones...");
        int statesProcessed = 0;
        
        for (int mask = 0; mask < totalStates; mask++) {
            for (int last = 0; last < citiesToVisitCount; last++) {
                if ((mask & (1 << last)) == 0) continue;
                if (dp[mask][last] == Double.POSITIVE_INFINITY) continue;
                
                for (int next = 0; next < citiesToVisitCount; next++) {
                    if ((mask & (1 << next)) != 0) continue;
                    
                    int newMask = mask | (1 << next);
                    double newCost = dp[mask][last] + costMatrix[last + 1][next + 1];
                    
                    if (newCost < dp[newMask][next]) {
                        dp[newMask][next] = newCost;
                        parent[newMask][next] = last;
                        statesProcessed++;
                        
                        if (statesProcessed % 10 == 0) {
                            System.out.println("  📈 Estados procesados: " + statesProcessed);
                        }
                    }
                }
            }
        }
        System.out.println("✅ Total de estados procesados: " + statesProcessed);
        
        // FASE 5: BÚSQUEDA DE LA MEJOR SOLUCIÓN
        System.out.println("\n--- FASE 5: Búsqueda de Solución Óptima ---");
        int allVisited = (1 << citiesToVisitCount) - 1;
        double minCost = Double.POSITIVE_INFINITY;
        int bestLast = -1;
        
        System.out.println("🔍 Buscando la mejor ruta final (máscara completa: " + allVisited + ")...");
        for (int i = 0; i < citiesToVisitCount; i++) {
            if (dp[allVisited][i] < minCost) {
                minCost = dp[allVisited][i];
                bestLast = i;
                System.out.println("  🏆 Nueva mejor opción: terminando en " + citiesToVisit.get(i) + 
                    " con costo " + String.format("%.2f", minCost));
            }
        }
        
        if (minCost == Double.POSITIVE_INFINITY) {
            System.out.println("❌ No se encontró ruta válida que visite todas las ciudades");
            return new MultiCityTourDto(
                List.of(), 0, null, null, null, List.of(), 
                "No se encontró ruta que visite todas las ciudades", 0.0
            );
        }
        
        System.out.println("✅ Solución óptima encontrada con costo total: " + String.format("%.2f", minCost));
        
        // FASE 6: RECONSTRUCCIÓN DEL CAMINO
        System.out.println("\n--- FASE 6: Reconstrucción del Camino Óptimo ---");
        List<String> tourOrder = new ArrayList<>();
        List<TourSegment> segments = new ArrayList<>();
        int mask = allVisited;
        int current = bestLast;
        
        Stack<Integer> visitOrder = new Stack<>();
        System.out.println("🔙 Reconstruyendo ruta desde el final...");
        while (current != -1) {
            visitOrder.push(current);
            System.out.println("  ← Ciudad #" + current + ": " + citiesToVisit.get(current));
            int prev = parent[mask][current];
            if (prev != -1) {
                mask ^= (1 << current);
            }
            current = prev;
        }
        
        System.out.println("\n🗺️  RUTA ÓPTIMA ENCONTRADA:");
        tourOrder.add(startCity);
        String prevCity = startCity;
        int prevIdx = 0;
        
        Double totalPrice = 0.0;
        Double totalDuration = 0.0;
        Double totalDistance = 0.0;
        
        int segmentNum = 1;
        while (!visitOrder.isEmpty()) {
            int cityIdx = visitOrder.pop();
            String nextCity = citiesToVisit.get(cityIdx);
            tourOrder.add(nextCity);
            
            String cacheKey = prevCity + "→" + nextCity;
            GreedyPathDto segmentPath = pathCache.get(cacheKey);
            
            if (segmentPath == null) {
                segmentPath = greedyService.greedyPathByCity(prevCity, nextCity, criterion, null, null, null);
            }
            
            TourSegment segment = new TourSegment(
                prevCity, nextCity, segmentPath.segments(), costMatrix[prevIdx][cityIdx + 1]
            );
            segments.add(segment);
            
            System.out.println("  " + segmentNum + ". " + prevCity + " → " + nextCity + 
                String.format(" | Precio: €%.2f, Duración: %.2fh, Distancia: %.0fkm",
                segmentPath.totalPrice() != null ? segmentPath.totalPrice() : 0,
                segmentPath.totalDuration() != null ? segmentPath.totalDuration() : 0,
                segmentPath.totalDistance() != null ? segmentPath.totalDistance() : 0));
            
            if (segmentPath.totalPrice() != null) totalPrice += segmentPath.totalPrice();
            if (segmentPath.totalDuration() != null) totalDuration += segmentPath.totalDuration();
            if (segmentPath.totalDistance() != null) totalDistance += segmentPath.totalDistance();
            
            prevCity = nextCity;
            prevIdx = cityIdx + 1;
            segmentNum++;
        }
        
        long endTime = System.nanoTime();
        double executionTimeMs = (endTime - startTime) / 1_000_000.0;
        
        System.out.println("\n═══════════════════════════════════════════════════════════════");
        System.out.println("🎉 TOUR ÓPTIMO COMPLETADO");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("🗺️  Ruta: " + String.join(" → ", tourOrder));
        System.out.println("💰 Precio total: €" + String.format("%.2f", totalPrice));
        System.out.println("⏱️  Duración total: " + String.format("%.2f", totalDuration) + " horas");
        System.out.println("📏 Distancia total: " + String.format("%.0f", totalDistance) + " km");
        System.out.println("⚡ Tiempo de ejecución: " + String.format("%.2f", executionTimeMs) + " ms");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        return new MultiCityTourDto(
            tourOrder, citiesToVisitCount,
            totalPrice > 0 ? totalPrice : null,
            totalDuration > 0 ? totalDuration : null,
            totalDistance > 0 ? totalDistance : null,
            segments,
            "Tour óptimo encontrado usando Programación Dinámica (TSP)",
            executionTimeMs
        );
    }
    
    private double getCostFromPath(GreedyPathDto path, String criterion) {
        if (criterion == null) criterion = "price";
        
        return switch (criterion.toLowerCase()) {
            case "price" -> path.totalPrice() != null ? path.totalPrice() : Double.POSITIVE_INFINITY;
            case "duration" -> path.totalDuration() != null ? path.totalDuration() : Double.POSITIVE_INFINITY;
            case "distance" -> path.totalDistance() != null ? path.totalDistance() : Double.POSITIVE_INFINITY;
            default -> {
                double cost = 0.0;
                if (path.totalPrice() != null) cost += 0.33 * path.totalPrice();
                if (path.totalDuration() != null) cost += 0.33 * path.totalDuration() * 20;
                if (path.totalDistance() != null) cost += 0.34 * path.totalDistance() / 10;
                yield cost;
            }
        };
    }
}

