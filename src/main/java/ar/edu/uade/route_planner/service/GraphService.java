package ar.edu.uade.route_planner.service;

import ar.edu.uade.route_planner.domain.Connection;
import ar.edu.uade.route_planner.domain.Station;
import ar.edu.uade.route_planner.repo.StationRepo;
import ar.edu.uade.route_planner.repo.EdgeRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GraphService {
    private final StationRepo repo;

    // ==================== BFS ====================
    public PathDto bfs(String from, String to, int maxDepth) {
        if (Objects.equals(from, to)) return new PathDto(List.of(from), 0, List.of(from));

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
                        return new PathDto(path, path.size() - 1, new ArrayList<>(visited));
                    }
                    q.add(v);
                }
            }
            depth++;
        }
        return new PathDto(List.of(), -1, new ArrayList<>(visited));
    }

    // ==================== DFS ====================
    public PathDto dfs(String from, String to, int maxDepth) {
        Set<String> visited = new LinkedHashSet<>();
        List<String> path = new ArrayList<>();
        boolean found = dfsHelper(from, to, maxDepth, visited, path);
        return new PathDto(found ? path : List.of(), found ? path.size() - 1 : -1, new ArrayList<>(visited));
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

    // ==================== DIJKSTRA ====================
    public DijkstraResult dijkstra(String from, String to) {
        // Construir el grafo desde Neo4j
        Map<String, Map<String, Double>> graph = new HashMap<>();
        Set<String> allNodes = new HashSet<>();
        
        for (EdgeRecord row : repo.allEdges()) {
            if (row == null) continue;
            
            String src = row.from();
            String dst = row.to();
            Map<String, Object> edge = row.edge();
            
            // Validar que ninguno sea null
            if (src == null || dst == null || edge == null) continue;
            
            Object distObj = edge.get("dist");
            if (distObj == null) continue;
            
            double weight = ((Number) distObj).doubleValue();
            graph.computeIfAbsent(src, k -> new HashMap<>()).put(dst, weight);
            allNodes.add(src);
            allNodes.add(dst);
        }

        if (!allNodes.contains(from)) {
            throw new IllegalArgumentException("Nodo origen no existe: " + from);
        }
        if (!allNodes.contains(to)) {
            throw new IllegalArgumentException("Nodo destino no existe: " + to);
        }

        // Inicializar distancias
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        
        for (String node : allNodes) {
            dist.put(node, Double.POSITIVE_INFINITY);
        }
        dist.put(from, 0.0);

        // Priority Queue con comparador correcto
        PriorityQueue<NodeDistance> pq = new PriorityQueue<>(Comparator.comparingDouble(nd -> nd.distance));
        pq.add(new NodeDistance(from, 0.0));
        
        Set<String> visited = new HashSet<>();

        while (!pq.isEmpty()) {
            NodeDistance current = pq.poll();
            String u = current.node;
            
            if (visited.contains(u)) continue;
            visited.add(u);
            
            if (u.equals(to)) break;

            Map<String, Double> neighbors = graph.get(u);
            if (neighbors == null) continue;

            for (Map.Entry<String, Double> entry : neighbors.entrySet()) {
                String v = entry.getKey();
                double weight = entry.getValue();
                double alt = dist.get(u) + weight;
                
                if (alt < dist.get(v)) {
                    dist.put(v, alt);
                    prev.put(v, u);
                    pq.add(new NodeDistance(v, alt));
                }
            }
        }

        List<String> path = reconstruct(prev, from, to);
        double totalDist = dist.getOrDefault(to, Double.POSITIVE_INFINITY);
        
        return new DijkstraResult(path, path.isEmpty() ? -1 : path.size() - 1, totalDist);
    }

    // ==================== PRIM (Dirigido) ====================
    public PrimResult primDirected(String startCode) {
        // Construir el grafo
        Map<String, Map<String, Double>> graph = new HashMap<>();
        Set<String> allNodes = new HashSet<>();

        for (EdgeRecord row : repo.allEdges()) {
            String from = row.from();
            String to = row.to();
            Map<String, Object> edge = row.edge();

            Object distObj = edge.get("dist");
            if (distObj == null) continue;
            
            double weight = ((Number) distObj).doubleValue();
            graph.computeIfAbsent(from, k -> new HashMap<>()).put(to, weight);
            allNodes.add(from);
            allNodes.add(to);
        }

        if (!graph.containsKey(startCode)) {
            throw new IllegalArgumentException("No se encontraron conexiones salientes para " + startCode);
        }

        Set<String> visited = new HashSet<>();
        List<String> mstEdges = new ArrayList<>();
        double totalWeight = 0.0;

        // Priority Queue: [peso, origen, destino]
        PriorityQueue<EdgeWeight> pq = new PriorityQueue<>(Comparator.comparingDouble(e -> e.weight));

        visited.add(startCode);
        Map<String, Double> neighbors = graph.get(startCode);
        if (neighbors != null) {
            for (Map.Entry<String, Double> entry : neighbors.entrySet()) {
                pq.add(new EdgeWeight(entry.getValue(), startCode, entry.getKey()));
            }
        }

        while (!pq.isEmpty()) {
            EdgeWeight edge = pq.poll();
            
            if (visited.contains(edge.to)) continue;

            visited.add(edge.to);
            mstEdges.add(String.format("%s -- %.1fkm --> %s", edge.from, edge.weight, edge.to));
            totalWeight += edge.weight;

            Map<String, Double> nextNeighbors = graph.get(edge.to);
            if (nextNeighbors != null) {
                for (Map.Entry<String, Double> entry : nextNeighbors.entrySet()) {
                    if (!visited.contains(entry.getKey())) {
                        pq.add(new EdgeWeight(entry.getValue(), edge.to, entry.getKey()));
                    }
                }
            }
        }

        return new PrimResult(mstEdges, visited.size(), totalWeight);
    }

    // ==================== KRUSKAL ====================
    public KruskalResult kruskal() {
        List<Edge> allEdges = new ArrayList<>();
        Set<String> allNodes = new HashSet<>();

        // Recolectar todas las aristas
        for (EdgeRecord row : repo.allEdges()) {
            String from = row.from();
            String to = row.to();
            Map<String, Object> edge = row.edge();

            Object distObj = edge.get("dist");
            if (distObj == null) continue;

            double weight = ((Number) distObj).doubleValue();
            allEdges.add(new Edge(from, to, weight));
            allNodes.add(from);
            allNodes.add(to);
        }

        // Ordenar aristas por peso
        allEdges.sort(Comparator.comparingDouble(e -> e.weight));

        // Union-Find (Disjoint Set Union)
        UnionFind uf = new UnionFind(allNodes);
        
        List<String> mstEdges = new ArrayList<>();
        double totalWeight = 0.0;
        int edgeCount = 0;

        for (Edge edge : allEdges) {
            if (uf.union(edge.from, edge.to)) {
                mstEdges.add(String.format("%s -- %.1fkm --> %s", edge.from, edge.weight, edge.to));
                totalWeight += edge.weight;
                edgeCount++;
                
                // MST completo cuando tiene n-1 aristas
                if (edgeCount == allNodes.size() - 1) break;
            }
        }

        return new KruskalResult(mstEdges, edgeCount, totalWeight);
    }

    // ==================== MÉTODOS AUXILIARES ====================
    private List<String> reconstruct(Map<String, String> parent, String from, String to) {
        LinkedList<String> path = new LinkedList<>();
        for (String cur = to; cur != null; cur = parent.get(cur)) {
            path.addFirst(cur);
        }
        return !path.isEmpty() && path.getFirst().equals(from) ? path : List.of();
    }

    // ==================== CLASES AUXILIARES ====================
    
    // Para Dijkstra
    private static class NodeDistance {
        String node;
        double distance;
        
        NodeDistance(String node, double distance) {
            this.node = node;
            this.distance = distance;
        }
    }

    // Para Prim
    private static class EdgeWeight {
        double weight;
        String from;
        String to;
        
        EdgeWeight(double weight, String from, String to) {
            this.weight = weight;
            this.from = from;
            this.to = to;
        }
    }

    // Para Kruskal
    private static class Edge {
        String from;
        String to;
        double weight;

        Edge(String from, String to, double weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }
    }

    // Union-Find para Kruskal
    private static class UnionFind {
        Map<String, String> parent = new HashMap<>();
        Map<String, Integer> rank = new HashMap<>();

        UnionFind(Set<String> nodes) {
            for (String node : nodes) {
                parent.put(node, node);
                rank.put(node, 0);
            }
        }

        String find(String x) {
            if (!parent.get(x).equals(x)) {
                parent.put(x, find(parent.get(x))); // Path compression
            }
            return parent.get(x);
        }

        boolean union(String x, String y) {
            String rootX = find(x);
            String rootY = find(y);

            if (rootX.equals(rootY)) return false; // Ya están conectados

            // Union by rank
            int rankX = rank.get(rootX);
            int rankY = rank.get(rootY);

            if (rankX < rankY) {
                parent.put(rootX, rootY);
            } else if (rankX > rankY) {
                parent.put(rootY, rootX);
            } else {
                parent.put(rootY, rootX);
                rank.put(rootX, rankX + 1);
            }

            return true;
        }
    }

    // ==================== RECORDS (DTOs) ====================
    
    public record PathDto(List<String> path, int hops, List<String> visited) {}
    
    public record DijkstraResult(List<String> path, int hops, double totalDistance) {}
    
    public record PrimResult(List<String> edges, int nodesConnected, double totalWeight) {}
    
    public record KruskalResult(List<String> edges, int edgeCount, double totalWeight) {}
}