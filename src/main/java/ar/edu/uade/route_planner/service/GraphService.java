// ============================================
// GraphService.java
// ============================================
package ar.edu.uade.route_planner.service;

import ar.edu.uade.route_planner.domain.Connection;
import ar.edu.uade.route_planner.domain.Station;
import ar.edu.uade.route_planner.repo.EdgeRecord;
import ar.edu.uade.route_planner.repo.StationRepo;
import ar.edu.uade.route_planner.service.GraphService.MSTEdge;
import lombok.RequiredArgsConstructor;

import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GraphService {
    private final StationRepo repo;

    // ==================== BFS ====================
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

    // ==================== DFS ====================
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

    // ==================== DIJKSTRA ====================
    public PathDto dijkstra(String from, String to) {
        if (Objects.equals(from, to)) return new PathDto(List.of(from), 0, List.of(from), 0.0);

        // Mapa de distancias mínimas desde 'from'
        Map<String, Double> distance = new HashMap<>();
        // Mapa de padres para reconstruir el camino
        Map<String, String> parent = new HashMap<>();
        // Set de nodos visitados (en orden)
        Set<String> visited = new LinkedHashSet<>();
        // PriorityQueue para procesar el nodo con menor distancia
        PriorityQueue<NodeDistance> pq = new PriorityQueue<>(Comparator.comparingDouble(nd -> nd.distance));

        distance.put(from, 0.0);
        pq.offer(new NodeDistance(from, 0.0));

        while (!pq.isEmpty()) {
            NodeDistance current = pq.poll();
            String u = current.code;

            // Si ya visitamos este nodo, skip
            if (visited.contains(u)) continue;
            visited.add(u);

            // Si llegamos al destino, reconstruir camino
            if (u.equals(to)) {
                List<String> path = reconstruct(parent, from, to);
                return new PathDto(path, path.size() - 1, new ArrayList<>(visited), distance.get(to));
            }

            // Expandir vecinos
            Station hop = repo.oneHop(u);
            if (hop == null || hop.edges == null) continue;

            for (Connection c : hop.edges) {
                String v = c.to.code;
                if (visited.contains(v)) continue;

                // Validar que dist no sea null
                if (c.dist == null) continue;

                double newDist = distance.get(u) + c.dist;

                // Si encontramos un camino más corto, actualizar
                if (!distance.containsKey(v) || newDist < distance.get(v)) {
                    distance.put(v, newDist);
                    parent.put(v, u);
                    pq.offer(new NodeDistance(v, newDist));
                }
            }
        }

        // No se encontró camino
        return new PathDto(List.of(), -1, new ArrayList<>(visited), Double.POSITIVE_INFINITY);
    }

    // Clase auxiliar para Dijkstra
    private static class NodeDistance {
        String code;
        double distance;

        NodeDistance(String code, double distance) {
            this.code = code;
            this.distance = distance;
        }
    }


public MSTDto prim(String startNode) {
    try {
        List<StationRepo.SimpleEdge> connections = repo.allEdgesSimple();
        
        if (connections == null || connections.isEmpty()) {
            return new MSTDto(List.of(), 0.0, 0, "No hay conexiones en la base de datos");
        }

        Set<String> nodes = new HashSet<>();
        Map<String, List<EdgeInfo>> adjacency = new HashMap<>();
        
        // Construir grafo no dirigido
        for (StationRepo.SimpleEdge conn : connections) {
            try {
                String from = conn.getFrom();
                String to = conn.getTo();
                Double dist = conn.getDist();
                
                if (from == null || to == null || dist == null) continue;
                
                nodes.add(from);
                nodes.add(to);
                
                // Agregar arista en ambas direcciones (no dirigido)
                adjacency.computeIfAbsent(from, k -> new ArrayList<>())
                         .add(new EdgeInfo(to, dist));
                adjacency.computeIfAbsent(to, k -> new ArrayList<>())
                         .add(new EdgeInfo(from, dist));
                
            } catch (Exception ex) {
                System.err.println("Error procesando conexión: " + ex.getMessage());
                continue;
            }
        }
        
        if (nodes.isEmpty()) {
            return new MSTDto(List.of(), 0.0, 0, "No se encontraron nodos válidos");
        }
        
        // Si el nodo inicial no existe, usar el primero disponible
        if (!nodes.contains(startNode)) {
            startNode = nodes.iterator().next();
        }

        Set<String> inMST = new HashSet<>();
        List<MSTEdge> mstEdges = new ArrayList<>();
        PriorityQueue<MSTCandidate> pq = new PriorityQueue<>(
            Comparator.comparingDouble(c -> c.weight)
        );
        
        inMST.add(startNode);
        
        // Agregar aristas desde el nodo inicial
        if (adjacency.containsKey(startNode)) {
            for (EdgeInfo edge : adjacency.get(startNode)) {
                if (!inMST.contains(edge.to)) {
                    pq.offer(new MSTCandidate(startNode, edge.to, edge.weight));
                }
            }
        }

        double totalWeight = 0.0;

        while (!pq.isEmpty() && inMST.size() < nodes.size()) {
            MSTCandidate candidate = pq.poll();
            
            if (inMST.contains(candidate.to)) continue;
            
            inMST.add(candidate.to);
            mstEdges.add(new MSTEdge(candidate.from, candidate.to, candidate.weight));
            totalWeight += candidate.weight;
            
            // Agregar nuevas aristas
            if (adjacency.containsKey(candidate.to)) {
                for (EdgeInfo edge : adjacency.get(candidate.to)) {
                    if (!inMST.contains(edge.to)) {
                        pq.offer(new MSTCandidate(candidate.to, edge.to, edge.weight));
                    }
                }
            }
        }

        String message = String.format("Prim MST: %d nodos conectados de %d totales, %d aristas", 
                                      inMST.size(), nodes.size(), mstEdges.size());
        if (inMST.size() < nodes.size()) {
            message += " - ADVERTENCIA: Grafo desconectado";
        }

        return new MSTDto(mstEdges, totalWeight, inMST.size(), message);
        
    } catch (Exception e) {
        e.printStackTrace();
        return new MSTDto(List.of(), 0.0, 0, "Error: " + e.getMessage());
    }
}

// ==================== GREEDY: KRUSKAL (MST) ====================
public MSTDto kruskal() {
    try {
        List<StationRepo.SimpleEdge> connections = repo.allEdgesSimple();
        
        if (connections == null || connections.isEmpty()) {
            return new MSTDto(List.of(), 0.0, 0, "No hay conexiones en la base de datos");
        }

        Set<String> nodes = new HashSet<>();
        List<WeightedEdge> edges = new ArrayList<>();
        Set<String> processedPairs = new HashSet<>();

        // Convertir a lista de aristas no dirigidas
        for (StationRepo.SimpleEdge conn : connections) {
            try {
                String from = conn.getFrom();
                String to = conn.getTo();
                Double dist = conn.getDist();
                
                if (from == null || to == null || dist == null) continue;
                
                nodes.add(from);
                nodes.add(to);
                
                // Crear par único (evitar duplicados A-B y B-A)
                String pair = from.compareTo(to) < 0 
                    ? from + "|" + to 
                    : to + "|" + from;
                
                if (!processedPairs.contains(pair)) {
                    edges.add(new WeightedEdge(from, to, dist));
                    processedPairs.add(pair);
                }
                
            } catch (Exception ex) {
                System.err.println("Error procesando conexión: " + ex.getMessage());
                continue;
            }
        }

        if (nodes.isEmpty()) {
            return new MSTDto(List.of(), 0.0, 0, "No se encontraron nodos válidos");
        }

        // Ordenar aristas por peso (de menor a mayor distancia)
        edges.sort(Comparator.comparingDouble(e -> e.weight));

        // Union-Find para detectar ciclos
        UnionFind uf = new UnionFind(nodes);
        List<MSTEdge> mstEdges = new ArrayList<>();
        double totalWeight = 0.0;

        for (WeightedEdge e : edges) {
            // Si conectar estos nodos NO crea un ciclo, agregar al MST
            if (uf.union(e.from, e.to)) {
                mstEdges.add(new MSTEdge(e.from, e.to, e.weight));
                totalWeight += e.weight;
                
                // Un MST tiene exactamente n-1 aristas
                if (mstEdges.size() == nodes.size() - 1) break;
            }
        }

        String message = String.format("Kruskal MST: %d nodos, %d aristas (esperadas: %d)", 
                                      nodes.size(), mstEdges.size(), nodes.size() - 1);
        if (mstEdges.size() < nodes.size() - 1) {
            message += " - ADVERTENCIA: Grafo desconectado";
        }

        return new MSTDto(mstEdges, totalWeight, nodes.size(), message);
        
    } catch (Exception e) {
        e.printStackTrace();
        return new MSTDto(List.of(), 0.0, 0, "Error: " + e.getMessage());
    }
}

// ==================== Clases auxiliares ====================

private static class EdgeInfo {
    String to;
    double weight;
    EdgeInfo(String to, double weight) {
        this.to = to;
        this.weight = weight;
    }
}

private static class MSTCandidate {
    String from, to;
    double weight;
    MSTCandidate(String from, String to, double weight) {
        this.from = from;
        this.to = to;
        this.weight = weight;
    }
}

private static class WeightedEdge {
    String from, to;
    double weight;
    WeightedEdge(String from, String to, double weight) {
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
            parent.put(x, find(parent.get(x)));
        }
        return parent.get(x);
    }

    boolean union(String x, String y) {
        String rootX = find(x);
        String rootY = find(y);
        
        if (rootX.equals(rootY)) return false;
        
        if (rank.get(rootX) < rank.get(rootY)) {
            parent.put(rootX, rootY);
        } else if (rank.get(rootX) > rank.get(rootY)) {
            parent.put(rootY, rootX);
        } else {
            parent.put(rootY, rootX);
            rank.put(rootX, rank.get(rootX) + 1);
        }
        return true;
    }
}
    // ==================== DTOs ====================
    public record PathDto(List<String> path, int hops, List<String> visited, double totalDistance) {}

   public record MSTEdge(String from, String to, double weight) {}
    public record MSTDto(List<MSTEdge> edges, double totalWeight, int nodeCount, String message) {}         
}