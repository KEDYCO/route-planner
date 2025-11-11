package ar.edu.uade.route_planner.service;

import ar.edu.uade.route_planner.service.dto.MSTDto;
import ar.edu.uade.route_planner.service.dto.MSTEdge;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════
 * ALGORITMOS: PRIM Y KRUSKAL (GREEDY + UNION-FIND)
 * ═══════════════════════════════════════════════════════════════
 * Servicio para algoritmos de Minimum Spanning Tree (MST)
 * 
 * ALGORITMOS IMPLEMENTADOS:
 * - PRIM: Algoritmo greedy para MST (crece desde un nodo)
 * - KRUSKAL: Algoritmo greedy con Union-Find para MST (ordena aristas)
 * 
 * Ambos encuentran el árbol de expansión mínima del grafo
 * ═══════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
public class MSTService {
    private final Neo4jClient neo4jClient;

    // ==================== PRIM (GREEDY MST) ====================
    // ALGORITMO: PRIM - Minimum Spanning Tree con enfoque greedy
    // Crece el árbol desde un nodo inicial, siempre eligiendo la arista más barata
    public MSTDto prim(String startNode) {
        try {
            List<SimpleEdgeData> connections = getAllEdges();
            
            if (connections == null || connections.isEmpty()) {
                return new MSTDto(List.of(), 0.0, 0, "No hay conexiones en la base de datos");
            }

            Set<String> nodes = new HashSet<>();
            Map<String, List<EdgeInfo>> adjacency = new HashMap<>();
            
            // Construir grafo no dirigido
            for (SimpleEdgeData conn : connections) {
                try {
                    String from = conn.from;
                    String to = conn.to;
                    Double dist = conn.dist;
                    
                    if (from == null || to == null || dist == null) continue;
                    
                    nodes.add(from);
                    nodes.add(to);
                    
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
            
            if (!nodes.contains(startNode)) {
                startNode = nodes.iterator().next();
            }

            Set<String> inMST = new HashSet<>();
            List<MSTEdge> mstEdges = new ArrayList<>();
            PriorityQueue<MSTCandidate> pq = new PriorityQueue<>(
                Comparator.comparingDouble(c -> c.weight)
            );
            
            inMST.add(startNode);
            
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

    // ==================== KRUSKAL (GREEDY + UNION-FIND) ====================
    // ALGORITMO: KRUSKAL - MST ordenando aristas y usando Union-Find
    // Ordena todas las aristas por peso y las agrega si no forman ciclo
    public MSTDto kruskal() {
        try {
            List<SimpleEdgeData> connections = getAllEdges();
            
            if (connections == null || connections.isEmpty()) {
                return new MSTDto(List.of(), 0.0, 0, "No hay conexiones en la base de datos");
            }

            Set<String> nodes = new HashSet<>();
            List<WeightedEdge> edges = new ArrayList<>();
            Set<String> processedPairs = new HashSet<>();

            for (SimpleEdgeData conn : connections) {
                try {
                    String from = conn.from;
                    String to = conn.to;
                    Double dist = conn.dist;
                    
                    if (from == null || to == null || dist == null) continue;
                    
                    nodes.add(from);
                    nodes.add(to);
                    
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

            edges.sort(Comparator.comparingDouble(e -> e.weight));

            UnionFind uf = new UnionFind(nodes);
            List<MSTEdge> mstEdges = new ArrayList<>();
            double totalWeight = 0.0;

            for (WeightedEdge e : edges) {
                if (uf.union(e.from, e.to)) {
                    mstEdges.add(new MSTEdge(e.from, e.to, e.weight));
                    totalWeight += e.weight;
                    
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

    // ==================== UTILIDADES PRIVADAS ====================
    
    private List<SimpleEdgeData> getAllEdges() {
        return neo4jClient.query("""
            MATCH (s:Station)-[e:CONNECTS]->(t:Station)
            WHERE e.dist IS NOT NULL
            RETURN s.code as from, t.code as to, e.dist as dist
            """)
            .fetch()
            .all()
            .stream()
            .map(record -> {
                Object fromObj = record.get("from");
                Object toObj = record.get("to");
                Object distObj = record.get("dist");
                
                String from = fromObj == null ? null : fromObj.toString();
                String to = toObj == null ? null : toObj.toString();
                Double dist = distObj == null ? null : 
                    (distObj instanceof Number ? ((Number) distObj).doubleValue() : Double.parseDouble(distObj.toString()));
                
                return new SimpleEdgeData(from, to, dist);
            })
            .toList();
    }

    // ==================== CLASES AUXILIARES ====================

    private record SimpleEdgeData(String from, String to, Double dist) {}
    
    private record EdgeInfo(String to, double weight) {}
    
    private record MSTCandidate(String from, String to, double weight) {}
    
    private record WeightedEdge(String from, String to, double weight) {}

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
}

