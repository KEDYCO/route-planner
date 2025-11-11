package ar.edu.uade.route_planner.service;

import ar.edu.uade.route_planner.domain.Connection;
import ar.edu.uade.route_planner.domain.Station;
import ar.edu.uade.route_planner.repo.StationRepo;
import ar.edu.uade.route_planner.service.dto.GreedyPathDto;
import ar.edu.uade.route_planner.service.dto.TripSegment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════
 * ALGORITMO: GREEDY (CODICIOSO)
 * ═══════════════════════════════════════════════════════════════
 * Servicio para algoritmos greedy de búsqueda de rutas multi-criterio
 * 
 * ALGORITMO IMPLEMENTADO:
 * - GREEDY (Algoritmo Codicioso) - Toma la mejor decisión local en cada paso
 *   · Optimiza por precio, duración, distancia o combinación
 *   · No garantiza óptimo global, pero es eficiente
 * ═══════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
public class GreedyPathfindingService {
    private final StationRepo repo;

    // ==================== GREEDY BY CITY ====================
    // ALGORITMO: GREEDY - Selección codiciosa de mejor ruta entre ciudades
    public GreedyPathDto greedyPathByCity(String fromCity, String toCity, String criterion,
                                          Double priceWeight, Double durationWeight, Double distWeight) {
        List<Station> fromStations = repo.findStationsByCity(fromCity);
        List<Station> toStations = repo.findStationsByCity(toCity);
        
        if (fromStations.isEmpty()) {
            return new GreedyPathDto(List.of(), -1, List.of(), List.of(), null, null, null, criterion);
        }
        
        if (toStations.isEmpty()) {
            return new GreedyPathDto(List.of(), -1, List.of(), List.of(), null, null, null, criterion);
        }
        
        GreedyPathDto bestPath = null;
        double bestCost = Double.POSITIVE_INFINITY;
        
        boolean hasCustomWeights = priceWeight != null || durationWeight != null || distWeight != null;
        String criterionLower = hasCustomWeights ? "custom" : 
                               (criterion != null ? criterion.toLowerCase() : "combined");
        
        for (Station fromStation : fromStations) {
            for (Station toStation : toStations) {
                GreedyPathDto path = greedyPath(fromStation.code, toStation.code, criterionLower, 
                                               priceWeight, durationWeight, distWeight);
                
                if (path.path().isEmpty()) continue;
                
                double cost = calculatePathCost(path, criterionLower, priceWeight, durationWeight, distWeight);
                
                if (cost < bestCost) {
                    bestCost = cost;
                    bestPath = path;
                }
            }
        }
        
        if (bestPath != null && hasCustomWeights) {
            return new GreedyPathDto(
                bestPath.path(),
                bestPath.hops(),
                bestPath.visited(),
                bestPath.segments(),
                bestPath.totalPrice(),
                bestPath.totalDuration(),
                bestPath.totalDistance(),
                "custom"
            );
        }
        
        return bestPath != null ? bestPath : 
            new GreedyPathDto(List.of(), -1, List.of(), List.of(), null, null, null, criterionLower);
    }

    // ==================== GREEDY MULTI-CRITERIA ====================
    // ALGORITMO: GREEDY - Selección codiciosa con múltiples criterios
    public GreedyPathDto greedyPath(String from, String to, String criterion, 
                              Double priceWeight, Double durationWeight, Double distWeight) {
        if (Objects.equals(from, to)) {
            return new GreedyPathDto(List.of(from), 0, List.of(from), List.of(), 0.0, 0.0, 0.0, criterion);
        }

        if (priceWeight == null || durationWeight == null || distWeight == null) {
            double[] weights = getDefaultWeights(criterion);
            priceWeight = weights[0];
            durationWeight = weights[1];
            distWeight = weights[2];
        }

        Map<String, Double> cost = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        Map<String, Connection> edgeUsed = new HashMap<>();
        Set<String> visited = new LinkedHashSet<>();
        PriorityQueue<NodeCost> pq = new PriorityQueue<>(Comparator.comparingDouble(nc -> nc.cost));

        cost.put(from, 0.0);
        pq.offer(new NodeCost(from, 0.0));

        final double fPriceW = priceWeight;
        final double fDurationW = durationWeight;
        final double fDistW = distWeight;

        while (!pq.isEmpty()) {
            NodeCost current = pq.poll();
            String u = current.code;

            if (visited.contains(u)) continue;
            visited.add(u);

            if (u.equals(to)) {
                return buildGreedyPath(from, to, parent, edgeUsed, visited, criterion);
            }

            Station hop = repo.oneHop(u);
            if (hop == null || hop.edges == null) continue;

            for (Connection c : hop.edges) {
                String v = c.to.code;
                if (visited.contains(v)) continue;

                double edgeCost = calculateEdgeCost(c, fPriceW, fDurationW, fDistW);
                if (edgeCost == 0.0) continue;

                double newCost = cost.get(u) + edgeCost;

                if (!cost.containsKey(v) || newCost < cost.get(v)) {
                    cost.put(v, newCost);
                    parent.put(v, u);
                    edgeUsed.put(v, c);
                    pq.offer(new NodeCost(v, newCost));
                }
            }
        }

        return new GreedyPathDto(List.of(), -1, new ArrayList<>(visited), List.of(), null, null, null, criterion);
    }

    // ==================== UTILIDADES PRIVADAS ====================
    
    private double calculatePathCost(GreedyPathDto path, String criterion, 
                                    Double priceWeight, Double durationWeight, Double distWeight) {
        return switch (criterion.toLowerCase()) {
            case "price" -> path.totalPrice() != null ? path.totalPrice() : Double.POSITIVE_INFINITY;
            case "duration" -> path.totalDuration() != null ? path.totalDuration() : Double.POSITIVE_INFINITY;
            case "distance" -> path.totalDistance() != null ? path.totalDistance() : Double.POSITIVE_INFINITY;
            case "balanced" -> {
                double cost = 0.0;
                double pw = priceWeight != null ? priceWeight : 0.4;
                double dw = durationWeight != null ? durationWeight : 0.4;
                double distw = distWeight != null ? distWeight : 0.2;
                if (path.totalPrice() != null) cost += pw * path.totalPrice();
                if (path.totalDuration() != null) cost += dw * path.totalDuration() * 20;
                if (path.totalDistance() != null) cost += distw * path.totalDistance() / 10;
                yield cost;
            }
            case "custom" -> {
                double cost = 0.0;
                double cpw = priceWeight != null ? priceWeight : 0.33;
                double cdw = durationWeight != null ? durationWeight : 0.33;
                double cdistw = distWeight != null ? distWeight : 0.34;
                if (path.totalPrice() != null) cost += cpw * path.totalPrice();
                if (path.totalDuration() != null) cost += cdw * path.totalDuration() * 20;
                if (path.totalDistance() != null) cost += cdistw * path.totalDistance() / 10;
                yield cost;
            }
            default -> {
                double cost = 0.0;
                if (path.totalPrice() != null) cost += 0.33 * path.totalPrice();
                if (path.totalDuration() != null) cost += 0.33 * path.totalDuration() * 20;
                if (path.totalDistance() != null) cost += 0.34 * path.totalDistance() / 10;
                yield cost;
            }
        };
    }

    private double[] getDefaultWeights(String criterion) {
        return switch (criterion.toLowerCase()) {
            case "price" -> new double[]{1.0, 0.0, 0.0};
            case "duration" -> new double[]{0.0, 1.0, 0.0};
            case "distance" -> new double[]{0.0, 0.0, 1.0};
            case "balanced" -> new double[]{0.4, 0.4, 0.2};
            default -> new double[]{0.33, 0.33, 0.34};
        };
    }

    private double calculateEdgeCost(Connection c, double priceW, double durationW, double distW) {
        double edgeCost = 0.0;
        if (c.price != null && priceW > 0) {
            edgeCost += priceW * (c.price / 100.0);
        }
        if (c.duration != null && durationW > 0) {
            edgeCost += durationW * (c.duration / 10.0);
        }
        if (c.dist != null && distW > 0) {
            edgeCost += distW * (c.dist / 1000.0);
        }
        return edgeCost;
    }

    private GreedyPathDto buildGreedyPath(String from, String to, Map<String, String> parent, 
                                         Map<String, Connection> edgeUsed, Set<String> visited, String criterion) {
        List<String> path = reconstruct(parent, from, to);
        List<TripSegment> segments = new ArrayList<>();
        Double totalPrice = 0.0;
        Double totalDuration = 0.0;
        Double totalDistance = 0.0;
        
        for (int i = 0; i < path.size() - 1; i++) {
            String fromNode = path.get(i);
            String toNode = path.get(i + 1);
            Connection conn = edgeUsed.get(toNode);
            
            if (conn != null) {
                Station fromStation = repo.oneHop(fromNode);
                String fromName = fromStation != null ? fromStation.name : fromNode;
                
                TripSegment segment = new TripSegment(
                    fromNode, fromName, toNode, conn.to.name,
                    conn.mode, conn.carrier, conn.price, conn.duration, conn.dist
                );
                segments.add(segment);
                
                if (conn.price != null) totalPrice += conn.price;
                if (conn.duration != null) totalDuration += conn.duration;
                if (conn.dist != null) totalDistance += conn.dist;
            }
        }
        
        return new GreedyPathDto(
            path, 
            path.size() - 1, 
            new ArrayList<>(visited),
            segments,
            totalPrice > 0 ? totalPrice : null,
            totalDuration > 0 ? totalDuration : null,
            totalDistance > 0 ? totalDistance : null,
            criterion
        );
    }

    private List<String> reconstruct(Map<String, String> parent, String from, String to) {
        LinkedList<String> path = new LinkedList<>();
        String cur = to;
        while (cur != null) {
            path.addFirst(cur);
            if (cur.equals(from)) break;
            cur = parent.get(cur);
        }
        if (!path.isEmpty() && path.getFirst().equals(from)) {
            return new ArrayList<>(path);
        }
        return List.of();
    }

    private record NodeCost(String code, double cost) {}
}

