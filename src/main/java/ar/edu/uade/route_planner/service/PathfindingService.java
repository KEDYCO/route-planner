package ar.edu.uade.route_planner.service;

import ar.edu.uade.route_planner.domain.Connection;
import ar.edu.uade.route_planner.domain.Station;
import ar.edu.uade.route_planner.repo.StationRepo;
import ar.edu.uade.route_planner.service.dto.PathDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════
 * ALGORITMOS: BFS, DFS, DIJKSTRA
 * ═══════════════════════════════════════════════════════════════
 * Servicio para algoritmos básicos de búsqueda de caminos
 * 
 * ALGORITMOS IMPLEMENTADOS:
 * - BFS (Breadth-First Search) - Búsqueda en anchura
 * - DFS (Depth-First Search) - Búsqueda en profundidad  
 * - Dijkstra - Camino más corto (Shortest Path)
 * ═══════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
public class PathfindingService {
    private final StationRepo repo;

    // ==================== BFS (BREADTH-FIRST SEARCH) ====================
    // ALGORITMO: BFS - Búsqueda en anchura
    // Explora nivel por nivel hasta encontrar el destino
    public PathDto bfs(String from, String to, int maxDepth) {
        if (Objects.equals(from, to)) return new PathDto(List.of(from), 0, List.of(from), 0.0);

        Queue<String> q = new ArrayDeque<>();
        Map<String, String> parent = new HashMap<>();
        Set<String> visited = new LinkedHashSet<>();

        q.add(from);
        visited.add(from);
        int depth = 0;

        while (!q.isEmpty() && depth <= maxDepth) {
            int size = q.size();
            for (int i = 0; i < size; i++) {
                String u = q.poll();
                Station hop = repo.oneHop(u);
                if (hop == null || hop.edges == null) continue;

                for (Connection c : hop.edges) {
                    String v = c.to.code;
                    if (visited.contains(v)) continue;
                    visited.add(v);
                    parent.put(v, u);
                    if (v.equals(to)) {
                        List<String> path = reconstruct(parent, from, to);
                        return new PathDto(path, path.size() - 1, new ArrayList<>(visited), 0.0);
                    }
                    q.add(v);
                }
            }
            depth++;
        }
        return new PathDto(List.of(), -1, new ArrayList<>(visited), 0.0);
    }

    // ==================== DFS (DEPTH-FIRST SEARCH) ====================
    // ALGORITMO: DFS - Búsqueda en profundidad
    // Explora caminos hasta el fondo antes de retroceder
    public PathDto dfs(String from, String to, int maxDepth) {
        Set<String> visited = new LinkedHashSet<>();
        List<String> path = new ArrayList<>();
        boolean found = dfsHelper(from, to, maxDepth, visited, path);
        return new PathDto(found ? path : List.of(), found ? path.size() - 1 : -1, new ArrayList<>(visited), 0.0);
    }

    private boolean dfsHelper(String current, String target, int depth, Set<String> visited, List<String> path) {
        if (depth < 0) return false;
        visited.add(current);
        path.add(current);
        if (current.equals(target)) return true;

        Station hop = repo.oneHop(current);
        if (hop == null || hop.edges == null) {
            path.remove(path.size() - 1);
            return false;
        }

        for (Connection c : hop.edges) {
            String next = c.to.code;
            if (!visited.contains(next) && dfsHelper(next, target, depth - 1, visited, path)) {
                return true;
            }
        }
        path.remove(path.size() - 1);
        return false;
    }

    // ==================== DIJKSTRA (SHORTEST PATH) ====================
    // ALGORITMO: DIJKSTRA - Camino más corto ponderado
    // Encuentra el camino de menor distancia entre dos nodos
    public PathDto dijkstra(String from, String to) {
        if (Objects.equals(from, to)) return new PathDto(List.of(from), 0, List.of(from), 0.0);

        Map<String, Double> distance = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        Set<String> visited = new LinkedHashSet<>();
        PriorityQueue<NodeDistance> pq = new PriorityQueue<>(Comparator.comparingDouble(nd -> nd.distance));

        distance.put(from, 0.0);
        pq.offer(new NodeDistance(from, 0.0));

        while (!pq.isEmpty()) {
            NodeDistance current = pq.poll();
            String u = current.code;

            if (visited.contains(u)) continue;
            visited.add(u);

            if (u.equals(to)) {
                List<String> path = reconstruct(parent, from, to);
                return new PathDto(path, path.size() - 1, new ArrayList<>(visited), distance.get(to));
            }

            Station hop = repo.oneHop(u);
            if (hop == null || hop.edges == null) continue;

            for (Connection c : hop.edges) {
                String v = c.to.code;
                if (visited.contains(v)) continue;
                if (c.dist == null) continue;

                double newDist = distance.get(u) + c.dist;

                if (!distance.containsKey(v) || newDist < distance.get(v)) {
                    distance.put(v, newDist);
                    parent.put(v, u);
                    pq.offer(new NodeDistance(v, newDist));
                }
            }
        }

        return new PathDto(List.of(), -1, new ArrayList<>(visited), Double.POSITIVE_INFINITY);
    }

    // ==================== UTILIDADES ====================
    
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

    private record NodeDistance(String code, double distance) {}
}

