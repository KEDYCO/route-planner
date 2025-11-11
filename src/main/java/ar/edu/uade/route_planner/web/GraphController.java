package ar.edu.uade.route_planner.web;

import ar.edu.uade.route_planner.service.*;
import ar.edu.uade.route_planner.service.dto.*;
import ar.edu.uade.route_planner.repo.StationRepo;
import ar.edu.uade.route_planner.repo.StationRepo.SimpleEdge;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;



@RestController
@RequestMapping("/graph")
@RequiredArgsConstructor
@CrossOrigin
public class GraphController {
    private final PathfindingService pathfindingService;
    private final GreedyPathfindingService greedyService;
    private final MSTService mstService;
    private final CatalogService catalogService;
    private final TourPlannerService tourService;
    private final TripOptimizationService optimizationService;
    private final BacktrackingService backtrackingService;
    private final StationRepo repo;

    // Endpoint de diagnóstico
    @GetMapping("/debug/edges")
    public Map<String, Object> debugEdges() {
        List<SimpleEdge> edges = repo.allEdgesSimple();
        Map<String, Object> result = new HashMap<>();
        result.put("totalEdges", edges.size());
        result.put("edges", edges);
        return result;
    }

    // Endpoint para verificar si existe conexión directa
    @GetMapping("/debug/direct-connection")
    public Map<String, Object> checkDirectConnection(@RequestParam String from, @RequestParam String to) {
        ar.edu.uade.route_planner.domain.Station station = repo.oneHop(from);
        Map<String, Object> result = new HashMap<>();
        
        if (station == null) {
            result.put("error", "Station not found: " + from);
            result.put("directConnection", false);
            return result;
        }
        
        result.put("from", from);
        result.put("fromName", station.name);
        result.put("to", to);
        result.put("directConnection", false);
        
        if (station.edges != null) {
            for (var conn : station.edges) {
                if (conn.to.code.equals(to)) {
                    result.put("directConnection", true);
                    result.put("mode", conn.mode);
                    result.put("carrier", conn.carrier);
                    result.put("price", conn.price);
                    result.put("duration", conn.duration);
                    result.put("dist", conn.dist);
                    result.put("toName", conn.to.name);
                    break;
                }
            }
        }
        
        return result;
    }
    
    // Endpoint para debugear rutas posibles
    @GetMapping("/debug/all-paths")
    public Map<String, Object> debugAllPaths(@RequestParam String from, @RequestParam String to) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> paths = new ArrayList<>();
        
        // Ruta 1: Directa
        ar.edu.uade.route_planner.domain.Station fromStation = repo.oneHop(from);
        if (fromStation != null && fromStation.edges != null) {
            for (var conn : fromStation.edges) {
                if (conn.to.code.equals(to)) {
                    Map<String, Object> path = new HashMap<>();
                    path.put("route", from + " → " + to);
                    path.put("type", "direct");
                    path.put("totalPrice", conn.price);
                    path.put("details", List.of(
                        Map.of("from", from, "to", to, "mode", conn.mode, 
                               "carrier", conn.carrier, "price", conn.price)
                    ));
                    paths.add(path);
                }
            }
        }
        
        // Rutas con 1 parada intermedia
        if (fromStation != null && fromStation.edges != null) {
            for (var conn1 : fromStation.edges) {
                String intermediate = conn1.to.code;
                ar.edu.uade.route_planner.domain.Station intermediateStation = repo.oneHop(intermediate);
                
                if (intermediateStation != null && intermediateStation.edges != null) {
                    for (var conn2 : intermediateStation.edges) {
                        if (conn2.to.code.equals(to)) {
                            Double totalPrice = (conn1.price != null && conn2.price != null) 
                                ? conn1.price + conn2.price : null;
                            
                            Map<String, Object> path = new HashMap<>();
                            path.put("route", from + " → " + intermediate + " → " + to);
                            path.put("type", "1-stop");
                            path.put("totalPrice", totalPrice);
                            path.put("details", List.of(
                                Map.of("from", from, "to", intermediate, "mode", conn1.mode, 
                                       "carrier", conn1.carrier, "price", conn1.price),
                                Map.of("from", intermediate, "to", to, "mode", conn2.mode, 
                                       "carrier", conn2.carrier, "price", conn2.price)
                            ));
                            paths.add(path);
                        }
                    }
                }
            }
        }
        
        // Ordenar por precio
        paths.sort((p1, p2) -> {
            Double price1 = (Double) p1.get("totalPrice");
            Double price2 = (Double) p2.get("totalPrice");
            if (price1 == null) return 1;
            if (price2 == null) return -1;
            return Double.compare(price1, price2);
        });
        
        result.put("from", from);
        result.put("to", to);
        result.put("totalPaths", paths.size());
        result.put("paths", paths);
        
        if (!paths.isEmpty()) {
            result.put("cheapest", paths.get(0));
        }
        
        return result;
    }
    
    // Endpoint para ver conexiones desde un nodo específico
    @GetMapping("/debug/connections")
    public Map<String, Object> debugConnections(@RequestParam String from) {
        ar.edu.uade.route_planner.domain.Station station = repo.oneHop(from);
        Map<String, Object> result = new HashMap<>();
        
        if (station == null) {
            result.put("error", "Station not found: " + from);
            return result;
        }
        
        result.put("stationCode", station.code);
        result.put("stationName", station.name);
        result.put("stationType", station.type);
        
        if (station.edges != null) {
            List<Map<String, Object>> connections = new java.util.ArrayList<>();
            for (var conn : station.edges) {
                Map<String, Object> edge = new HashMap<>();
                edge.put("to", conn.to.code);
                edge.put("toName", conn.to.name);
                edge.put("mode", conn.mode);
                edge.put("carrier", conn.carrier);
                edge.put("price", conn.price);
                edge.put("duration", conn.duration);
                edge.put("dist", conn.dist);
                connections.add(edge);
            }
            result.put("connections", connections);
            result.put("totalConnections", connections.size());
        } else {
            result.put("connections", List.of());
            result.put("totalConnections", 0);
        }
        
        return result;
    }

    @GetMapping("/bfs")
    public PathDto bfs(@RequestParam String from,
                       @RequestParam String to,
                       @RequestParam(defaultValue = "6") int maxDepth) {
        return pathfindingService.bfs(from, to, maxDepth);
    }

    @GetMapping("/dfs")
    public PathDto dfs(@RequestParam String from, 
                       @RequestParam String to, 
                       @RequestParam(defaultValue = "6") int depth) {
        return pathfindingService.dfs(from, to, depth);
    }

    @GetMapping("/dijkstra")
    public PathDto dijkstra(@RequestParam String from, @RequestParam String to) {
        return pathfindingService.dijkstra(from, to);
    }

    @GetMapping("/greedy")
    public GreedyPathDto greedyPath(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "combined") String criterion,
            @RequestParam(required = false) Double priceWeight,
            @RequestParam(required = false) Double durationWeight,
            @RequestParam(required = false) Double distWeight) {
        return greedyService.greedyPath(from, to, criterion, priceWeight, durationWeight, distWeight);
    }

    @GetMapping("/greedy-city")
    public GreedyPathDto greedyPathByCity(
            @RequestParam String fromCity,
            @RequestParam String toCity,
            @RequestParam(defaultValue = "price") String criterion,
            @RequestParam(required = false) Double priceWeight,
            @RequestParam(required = false) Double durationWeight,
            @RequestParam(required = false) Double distWeight) {
        return greedyService.greedyPathByCity(fromCity, toCity, criterion, priceWeight, durationWeight, distWeight);
    }

    @GetMapping("/prim")
    public MSTDto prim(@RequestParam String start) {
        return mstService.prim(start);
    }
    
    
    @GetMapping("/kruskal")
    public MSTDto kruskal() {
        return mstService.kruskal();
    }

    @GetMapping("/catalog")
    public CatalogDto getCatalog(
            @RequestParam(defaultValue = "quicksort") String sortAlgorithm) {
        return catalogService.getCatalog(sortAlgorithm);
    }

    @GetMapping("/multi-city-tour")
    public MultiCityTourDto planMultiCityTour(
            @RequestParam String startCity,
            @RequestParam List<String> cities,
            @RequestParam(defaultValue = "price") String criterion) {
        return tourService.planMultiCityTour(startCity, cities, criterion);
    }

    @GetMapping("/optimize-trip")
    public OptimalTripDto optimizeTrip(
            @RequestParam String startCity,
            @RequestParam double budget,
            @RequestParam(required = false) Integer maxCities,
            @RequestParam(required = false) Boolean returnToOrigin) {
        return optimizationService.optimizeTrip(startCity, budget, maxCities, returnToOrigin);
    }

    @GetMapping("/all-paths")
    public AllPathsDto findAllPaths(
            @RequestParam String fromCity,
            @RequestParam String toCity,
            @RequestParam(required = false, defaultValue = "5") Integer maxStops) {
        return backtrackingService.findAllPaths(fromCity, toCity, maxStops);
    }

}