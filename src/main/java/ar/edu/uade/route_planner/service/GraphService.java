// ============================================
// GraphService.java
// ============================================
package ar.edu.uade.route_planner.service;

import ar.edu.uade.route_planner.domain.Connection;
import ar.edu.uade.route_planner.domain.Station;
import ar.edu.uade.route_planner.repo.StationRepo;
import lombok.RequiredArgsConstructor;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Stack;

@Service
@RequiredArgsConstructor
public class GraphService {
    private final StationRepo repo;
    private final Neo4jClient neo4jClient;
    
    // Método auxiliar para obtener todas las aristas usando Neo4jClient directamente
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
    
    // Clase auxiliar para almacenar datos de aristas
    private static class SimpleEdgeData {
        final String from;
        final String to;
        final Double dist;
        
        SimpleEdgeData(String from, String to, Double dist) {
            this.from = from;
            this.to = to;
            this.dist = dist;
        }
    }

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

    // ==================== GREEDY BY CITY ====================
    public GreedyPathDto greedyPathByCity(String fromCity, String toCity, String criterion,
                                          Double priceWeight, Double durationWeight, Double distWeight) {
        // Obtener todas las estaciones de las ciudades
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
        
        // Si se proporcionan pesos personalizados, usar criterio "custom"
        boolean hasCustomWeights = priceWeight != null || durationWeight != null || distWeight != null;
        String criterionLower = hasCustomWeights ? "custom" : 
                               (criterion != null ? criterion.toLowerCase() : "combined");
        
        // Probar todas las combinaciones de estaciones origen-destino
        for (Station fromStation : fromStations) {
            for (Station toStation : toStations) {
                GreedyPathDto path = greedyPath(fromStation.code, toStation.code, criterionLower, 
                                               priceWeight, durationWeight, distWeight);
                
                if (path.path().isEmpty()) continue;
                
                // Determinar el costo según el criterio
                double cost;
                switch (criterionLower) {
                    case "price":
                        cost = path.totalPrice() != null ? path.totalPrice() : Double.POSITIVE_INFINITY;
                        break;
                    case "duration":
                        cost = path.totalDuration() != null ? path.totalDuration() : Double.POSITIVE_INFINITY;
                        break;
                    case "distance":
                        cost = path.totalDistance() != null ? path.totalDistance() : Double.POSITIVE_INFINITY;
                        break;
                    case "balanced":
                        cost = 0.0;
                        double pWeight = priceWeight != null ? priceWeight : 0.4;
                        double dWeight = durationWeight != null ? durationWeight : 0.4;
                        double distWeight2 = distWeight != null ? distWeight : 0.2;
                        if (path.totalPrice() != null) cost += pWeight * path.totalPrice();
                        if (path.totalDuration() != null) cost += dWeight * path.totalDuration() * 20; // Normalizar duración
                        if (path.totalDistance() != null) cost += distWeight2 * path.totalDistance() / 10; // Normalizar distancia
                        break;
                    case "custom": // Pesos personalizados
                        cost = 0.0;
                        double customPw = priceWeight != null ? priceWeight : 0.33;
                        double customDw = durationWeight != null ? durationWeight : 0.33;
                        double customDistw = distWeight != null ? distWeight : 0.34;
                        if (path.totalPrice() != null) cost += customPw * path.totalPrice();
                        if (path.totalDuration() != null) cost += customDw * path.totalDuration() * 20; // Normalizar duración
                        if (path.totalDistance() != null) cost += customDistw * path.totalDistance() / 10; // Normalizar distancia
                        break;
                    default: // combined
                        cost = 0.0;
                        double pw = 0.33;
                        double dw = 0.33;
                        double distw = 0.34;
                        if (path.totalPrice() != null) cost += pw * path.totalPrice();
                        if (path.totalDuration() != null) cost += dw * path.totalDuration() * 20; // Normalizar duración
                        if (path.totalDistance() != null) cost += distw * path.totalDistance() / 10; // Normalizar distancia
                }
                
                if (cost < bestCost) {
                    bestCost = cost;
                    bestPath = path;
                }
            }
        }
        
        // Actualizar el criterio en la respuesta para reflejar el criterio real usado
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
    public GreedyPathDto greedyPath(String from, String to, String criterion, 
                              Double priceWeight, Double durationWeight, Double distWeight) {
        if (Objects.equals(from, to)) {
            return new GreedyPathDto(List.of(from), 0, List.of(from), List.of(), 0.0, 0.0, 0.0, criterion);
        }

        // Pesos por defecto según criterio
        if (priceWeight == null || durationWeight == null || distWeight == null) {
            switch (criterion.toLowerCase()) {
                case "price":
                    priceWeight = 1.0; durationWeight = 0.0; distWeight = 0.0;
                    break;
                case "duration":
                    priceWeight = 0.0; durationWeight = 1.0; distWeight = 0.0;
                    break;
                case "distance":
                    priceWeight = 0.0; durationWeight = 0.0; distWeight = 1.0;
                    break;
                case "balanced":
                    priceWeight = 0.4; durationWeight = 0.4; distWeight = 0.2;
                    break;
                default: // "combined"
                    priceWeight = 0.33; durationWeight = 0.33; distWeight = 0.34;
            }
        }

        Map<String, Double> cost = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        Map<String, Connection> edgeUsed = new HashMap<>(); // Para rastrear las conexiones usadas
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
                List<String> path = reconstruct(parent, from, to);
                
                // Construir segmentos del viaje con información de transporte
                List<TripSegment> segments = new ArrayList<>();
                Double totalPrice = 0.0;
                Double totalDuration = 0.0;
                Double totalDistance = 0.0;
                
                for (int i = 0; i < path.size() - 1; i++) {
                    String fromNode = path.get(i);
                    String toNode = path.get(i + 1);
                    Connection conn = edgeUsed.get(toNode);
                    
                    if (conn != null) {
                        // Obtener nombre del nodo origen
                        Station fromStation = repo.oneHop(fromNode);
                        String fromName = fromStation != null ? fromStation.name : fromNode;
                        
                        // Crear segmento del viaje
                        TripSegment segment = new TripSegment(
                            fromNode,
                            fromName,
                            toNode,
                            conn.to.name,
                            conn.mode,
                            conn.carrier,
                            conn.price,
                            conn.duration,
                            conn.dist
                        );
                        segments.add(segment);
                        
                        // Acumular costos
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

            Station hop = repo.oneHop(u);
            if (hop == null || hop.edges == null) continue;

            for (Connection c : hop.edges) {
                String v = c.to.code;
                if (visited.contains(v)) continue;

                // Calcular costo combinado (normalizado)
                double edgeCost = 0.0;
                
                if (c.price != null && fPriceW > 0) {
                    edgeCost += fPriceW * (c.price / 100.0); // Normalizar precio
                }
                if (c.duration != null && fDurationW > 0) {
                    edgeCost += fDurationW * (c.duration / 10.0); // Normalizar duración
                }
                if (c.dist != null && fDistW > 0) {
                    edgeCost += fDistW * (c.dist / 1000.0); // Normalizar distancia
                }

                // Si todos los criterios son null, skip
                if (edgeCost == 0.0) continue;

                double newCost = cost.get(u) + edgeCost;

                if (!cost.containsKey(v) || newCost < cost.get(v)) {
                    cost.put(v, newCost);
                    parent.put(v, u);
                    edgeUsed.put(v, c); // Guardar la conexión usada
                    pq.offer(new NodeCost(v, newCost));
                }
            }
        }

        return new GreedyPathDto(List.of(), -1, new ArrayList<>(visited), List.of(), null, null, null, criterion);
    }

    // Clase auxiliar para el algoritmo greedy
    private static class NodeCost {
        String code;
        double cost;

        NodeCost(String code, double cost) {
            this.code = code;
            this.cost = cost;
        }
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
        List<SimpleEdgeData> connections = getAllEdges();
        
        if (connections == null || connections.isEmpty()) {
            return new MSTDto(List.of(), 0.0, 0, "No hay conexiones en la base de datos");
        }

        Set<String> nodes = new HashSet<>();
        List<WeightedEdge> edges = new ArrayList<>();
        Set<String> processedPairs = new HashSet<>();

        // Convertir a lista de aristas no dirigidas
        for (SimpleEdgeData conn : connections) {
            try {
                String from = conn.from;
                String to = conn.to;
                Double dist = conn.dist;
                
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
    // ==================== DIVIDE Y VENCERÁS: CATÁLOGO ====================
    
    public CatalogDto getCatalog(String sortAlgorithm) {
        long startTime = System.nanoTime();
        
        // Obtener todos los datos de la BD usando Neo4jClient directamente
        List<StationInfo> stationList = neo4jClient.query("""
            MATCH (s:Station)-[:IN_CITY]->(c:City)
            OPTIONAL MATCH (s)-[conn:CONNECTS]->()
            RETURN s.code as stationCode, s.name as stationName, s.type as stationType, 
                   c.code as cityCode, c.name as cityName, c.country as country,
                   count(conn) as connectionCount
            ORDER BY s.code
            """)
            .fetch()
            .all()
            .stream()
            .map(record -> {
                String stationCode = record.get("stationCode") != null ? record.get("stationCode").toString() : null;
                String stationName = record.get("stationName") != null ? record.get("stationName").toString() : null;
                String stationType = record.get("stationType") != null ? record.get("stationType").toString() : null;
                String cityCode = record.get("cityCode") != null ? record.get("cityCode").toString() : null;
                String cityName = record.get("cityName") != null ? record.get("cityName").toString() : null;
                String country = record.get("country") != null ? record.get("country").toString() : null;
                Integer connectionCount = record.get("connectionCount") instanceof Number ? 
                    ((Number) record.get("connectionCount")).intValue() : 0;
                
                return new StationInfo(stationCode, stationName, stationType, cityCode, cityName, country, connectionCount);
            })
            .toList();
        
        // Agrupar por país
        Map<String, List<StationInfo>> byCountry = new java.util.TreeMap<>();
        for (StationInfo station : stationList) {
            byCountry.computeIfAbsent(station.country, k -> new ArrayList<>()).add(station);
        }
        
        // Ordenar estaciones dentro de cada país según el algoritmo elegido
        List<CountryData> countries = new ArrayList<>();
        for (Map.Entry<String, List<StationInfo>> entry : byCountry.entrySet()) {
            List<StationInfo> countryStations = new ArrayList<>(entry.getValue());
            
            // Aplicar algoritmo de ordenamiento (por número de conexiones, descendente)
            if ("mergesort".equalsIgnoreCase(sortAlgorithm)) {
                mergeSort(countryStations, 0, countryStations.size() - 1);
            } else {
                quickSort(countryStations, 0, countryStations.size() - 1);
            }
            
            // Agrupar por tipo (aeropuertos vs trenes)
            List<StationInfo> airports = countryStations.stream()
                .filter(s -> "AIRPORT".equals(s.type))
                .toList();
            List<StationInfo> trains = countryStations.stream()
                .filter(s -> "TRAIN".equals(s.type))
                .toList();
            
            // Obtener ciudades únicas
            Set<String> cities = countryStations.stream()
                .map(s -> s.cityName)
                .collect(java.util.stream.Collectors.toSet());
            
            countries.add(new CountryData(entry.getKey(), new ArrayList<>(cities), airports, trains));
        }
        
        // Top aeropuertos más conectados
        List<StationInfo> topStations = new ArrayList<>(stationList);
        if ("mergesort".equalsIgnoreCase(sortAlgorithm)) {
            mergeSort(topStations, 0, topStations.size() - 1);
        } else {
            quickSort(topStations, 0, topStations.size() - 1);
        }
        
        List<StationInfo> topAirports = topStations.stream()
            .filter(s -> "AIRPORT".equals(s.type))
            .limit(5)
            .toList();
        
        long endTime = System.nanoTime();
        double sortingTimeMs = (endTime - startTime) / 1_000_000.0;
        
        // Estadísticas
        long totalCountries = byCountry.size();
        long totalCities = stationList.stream().map(s -> s.cityName).distinct().count();
        long totalStations = stationList.size();
        long totalAirports = stationList.stream().filter(s -> "AIRPORT".equals(s.type)).count();
        long totalTrains = stationList.stream().filter(s -> "TRAIN".equals(s.type)).count();
        
        CatalogStats stats = new CatalogStats(
            (int) totalCountries,
            (int) totalCities,
            (int) totalStations,
            (int) totalAirports,
            (int) totalTrains
        );
        
        return new CatalogDto(stats, countries, topAirports, sortAlgorithm, sortingTimeMs);
    }
    
    // ==================== QUICKSORT ====================
    private void quickSort(List<StationInfo> list, int low, int high) {
        if (low < high) {
            int pi = partition(list, low, high);
            quickSort(list, low, pi - 1);
            quickSort(list, pi + 1, high);
        }
    }
    
    private int partition(List<StationInfo> list, int low, int high) {
        int pivot = list.get(high).connectionCount;
        int i = low - 1;
        
        for (int j = low; j < high; j++) {
            // Ordenar descendente por número de conexiones
            if (list.get(j).connectionCount >= pivot) {
                i++;
                // Swap
                StationInfo temp = list.get(i);
                list.set(i, list.get(j));
                list.set(j, temp);
            }
        }
        
        // Swap pivot
        StationInfo temp = list.get(i + 1);
        list.set(i + 1, list.get(high));
        list.set(high, temp);
        
        return i + 1;
    }
    
    // ==================== MERGESORT ====================
    private void mergeSort(List<StationInfo> list, int left, int right) {
        if (left < right) {
            int mid = left + (right - left) / 2;
            
            mergeSort(list, left, mid);
            mergeSort(list, mid + 1, right);
            
            merge(list, left, mid, right);
        }
    }
    
    private void merge(List<StationInfo> list, int left, int mid, int right) {
        // Tamaños de los subarrays
        int n1 = mid - left + 1;
        int n2 = right - mid;
        
        // Arrays temporales
        List<StationInfo> leftArray = new ArrayList<>(n1);
        List<StationInfo> rightArray = new ArrayList<>(n2);
        
        for (int i = 0; i < n1; i++) {
            leftArray.add(list.get(left + i));
        }
        for (int j = 0; j < n2; j++) {
            rightArray.add(list.get(mid + 1 + j));
        }
        
        // Merge
        int i = 0, j = 0, k = left;
        
        while (i < n1 && j < n2) {
            // Ordenar descendente por número de conexiones
            if (leftArray.get(i).connectionCount >= rightArray.get(j).connectionCount) {
                list.set(k, leftArray.get(i));
                i++;
            } else {
                list.set(k, rightArray.get(j));
                j++;
            }
            k++;
        }
        
        // Copiar elementos restantes
        while (i < n1) {
            list.set(k, leftArray.get(i));
            i++;
            k++;
        }
        
        while (j < n2) {
            list.set(k, rightArray.get(j));
            j++;
            k++;
        }
    }
    
    // Clases auxiliares para el catálogo
    public record StationInfo(
        String code,
        String name,
        String type,
        String cityCode,
        String cityName,
        String country,
        Integer connectionCount
    ) {}
    
    public record CountryData(
        String country,
        List<String> cities,
        List<StationInfo> airports,
        List<StationInfo> trainStations
    ) {}
    
    public record CatalogStats(
        int totalCountries,
        int totalCities,
        int totalStations,
        int totalAirports,
        int totalTrainStations
    ) {}
    
    public record CatalogDto(
        CatalogStats stats,
        List<CountryData> byCountry,
        List<StationInfo> topAirports,
        String sortingAlgorithm,
        double sortingTimeMs
    ) {}
    
    // ==================== PROGRAMACIÓN DINÁMICA: TOUR MULTI-CIUDAD ====================
    
    public MultiCityTourDto planMultiCityTour(String startCity, List<String> citiesToVisit, String criterion) {
        long startTime = System.nanoTime();
        System.out.println("🚀 Iniciando tour multi-ciudad: " + startCity + " → " + citiesToVisit);
        
        // Obtener todas las estaciones de cada ciudad
        Map<String, List<Station>> cityStations = new HashMap<>();
        System.out.println("📍 Obteniendo estaciones de: " + startCity);
        cityStations.put(startCity, repo.findStationsByCity(startCity));
        
        for (String city : citiesToVisit) {
            System.out.println("📍 Obteniendo estaciones de: " + city);
            cityStations.put(city, repo.findStationsByCity(city));
        }
        
        System.out.println("✅ Estaciones obtenidas. Verificando ciudades...");
        
        // Verificar que todas las ciudades existan
        for (Map.Entry<String, List<Station>> entry : cityStations.entrySet()) {
            if (entry.getValue().isEmpty()) {
                System.out.println("❌ Ciudad no encontrada: " + entry.getKey());
                return new MultiCityTourDto(
                    List.of(), 
                    0, 
                    null, 
                    null, 
                    null, 
                    List.of(), 
                    "Ciudad no encontrada: " + entry.getKey(),
                    0.0
                );
            }
        }
        
        // Calcular matriz de costos entre ciudades Y cachear las rutas
        List<String> allCities = new ArrayList<>();
        allCities.add(startCity);
        allCities.addAll(citiesToVisit);
        
        int n = allCities.size();
        double[][] costMatrix = new double[n][n];
        Map<String, GreedyPathDto> pathCache = new HashMap<>(); // CACHÉ para evitar recalcular
        
        System.out.println("🗺️ Calculando matriz de costos para " + n + " ciudades (" + (n*n-n) + " rutas)...");
        
        // Llenar matriz de costos y caché
        int totalRoutes = n * n - n;
        int currentRoute = 0;
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    costMatrix[i][j] = 0;
                } else {
                    currentRoute++;
                    String cacheKey = allCities.get(i) + "→" + allCities.get(j);
                    System.out.println("⏳ [" + currentRoute + "/" + totalRoutes + "] Calculando ruta: " + cacheKey);
                    
                    // Encontrar la mejor ruta entre ciudad i y ciudad j
                    GreedyPathDto bestPath = greedyPathByCity(
                        allCities.get(i), 
                        allCities.get(j), 
                        criterion != null ? criterion : "price",
                        null, null, null
                    );
                    
                    // Guardar en caché
                    pathCache.put(cacheKey, bestPath);
                    
                    if (bestPath.path().isEmpty()) {
                        costMatrix[i][j] = Double.POSITIVE_INFINITY;
                    } else {
                        // Determinar costo según criterio
                        double cost = getCostFromPath(bestPath, criterion);
                        costMatrix[i][j] = cost;
                    }
                }
            }
        }
        
        System.out.println("✅ Matriz de costos calculada. Iniciando algoritmo DP...");
        
        // TSP con Programación Dinámica (bitmask DP)
        int citiesToVisitCount = citiesToVisit.size();
        int totalStates = 1 << citiesToVisitCount; // 2^n estados
        System.out.println("🧮 DP: Procesando " + totalStates + " estados para " + citiesToVisitCount + " ciudades");
        
        // dp[mask][i] = costo mínimo para visitar ciudades en mask y terminar en ciudad i
        double[][] dp = new double[totalStates][citiesToVisitCount];
        int[][] parent = new int[totalStates][citiesToVisitCount];
        
        // Inicializar con infinito
        for (int mask = 0; mask < totalStates; mask++) {
            for (int i = 0; i < citiesToVisitCount; i++) {
                dp[mask][i] = Double.POSITIVE_INFINITY;
                parent[mask][i] = -1;
            }
        }
        
        // Estado inicial: visitar cada ciudad directamente desde el inicio
        for (int i = 0; i < citiesToVisitCount; i++) {
            dp[1 << i][i] = costMatrix[0][i + 1]; // +1 porque startCity es índice 0
        }
        
        // Llenar tabla DP
        for (int mask = 0; mask < totalStates; mask++) {
            for (int last = 0; last < citiesToVisitCount; last++) {
                if ((mask & (1 << last)) == 0) continue; // last no está en mask
                if (dp[mask][last] == Double.POSITIVE_INFINITY) continue;
                
                // Intentar visitar la siguiente ciudad
                for (int next = 0; next < citiesToVisitCount; next++) {
                    if ((mask & (1 << next)) != 0) continue; // next ya visitada
                    
                    int newMask = mask | (1 << next);
                    double newCost = dp[mask][last] + costMatrix[last + 1][next + 1];
                    
                    if (newCost < dp[newMask][next]) {
                        dp[newMask][next] = newCost;
                        parent[newMask][next] = last;
                    }
                }
            }
        }
        
        System.out.println("✅ DP completado. Buscando mejor solución...");
        
        // Encontrar la mejor ciudad final
        int allVisited = (1 << citiesToVisitCount) - 1;
        double minCost = Double.POSITIVE_INFINITY;
        int bestLast = -1;
        
        for (int i = 0; i < citiesToVisitCount; i++) {
            if (dp[allVisited][i] < minCost) {
                minCost = dp[allVisited][i];
                bestLast = i;
            }
        }
        
        if (minCost == Double.POSITIVE_INFINITY) {
            System.out.println("❌ No se encontró solución");
            return new MultiCityTourDto(
                List.of(), 
                0, 
                null, 
                null, 
                null, 
                List.of(), 
                "No se encontró ruta que visite todas las ciudades",
                0.0
            );
        }
        
        // Reconstruir el orden de visita
        List<String> tourOrder = new ArrayList<>();
        List<TourSegment> segments = new ArrayList<>();
        int mask = allVisited;
        int current = bestLast;
        
        // Reconstruir hacia atrás
        Stack<Integer> visitOrder = new Stack<>();
        while (current != -1) {
            visitOrder.push(current);
            int prev = parent[mask][current];
            if (prev != -1) {
                mask ^= (1 << current);
            }
            current = prev;
        }
        
        // Construir tour y segmentos
        tourOrder.add(startCity);
        String prevCity = startCity;
        int prevIdx = 0;
        
        Double totalPrice = 0.0;
        Double totalDuration = 0.0;
        Double totalDistance = 0.0;
        
        while (!visitOrder.isEmpty()) {
            int cityIdx = visitOrder.pop();
            String nextCity = citiesToVisit.get(cityIdx);
            tourOrder.add(nextCity);
            
            // Obtener detalles del segmento desde el caché (ya fue calculado)
            String cacheKey = prevCity + "→" + nextCity;
            GreedyPathDto segmentPath = pathCache.get(cacheKey);
            
            if (segmentPath == null) {
                // Fallback: calcular si no está en caché (no debería pasar)
                segmentPath = greedyPathByCity(prevCity, nextCity, criterion, null, null, null);
            }
            
            TourSegment segment = new TourSegment(
                prevCity,
                nextCity,
                segmentPath.segments(),
                costMatrix[prevIdx][cityIdx + 1]
            );
            segments.add(segment);
            
            if (segmentPath.totalPrice() != null) totalPrice += segmentPath.totalPrice();
            if (segmentPath.totalDuration() != null) totalDuration += segmentPath.totalDuration();
            if (segmentPath.totalDistance() != null) totalDistance += segmentPath.totalDistance();
            
            prevCity = nextCity;
            prevIdx = cityIdx + 1;
        }
        
        long endTime = System.nanoTime();
        double executionTimeMs = (endTime - startTime) / 1_000_000.0;
        
        System.out.println("🎉 Tour completado en " + executionTimeMs + " ms");
        System.out.println("📍 Orden: " + tourOrder);
        System.out.println("💰 Costo total: " + totalPrice);
        
        return new MultiCityTourDto(
            tourOrder,
            citiesToVisitCount,
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
    
    // DTOs para tour multi-ciudad
    public record TourSegment(
        String fromCity,
        String toCity,
        List<TripSegment> connections,
        double segmentCost
    ) {}
    
    public record MultiCityTourDto(
        List<String> tourOrder,
        int citiesVisited,
        Double totalPrice,
        Double totalDuration,
        Double totalDistance,
        List<TourSegment> segments,
        String message,
        double executionTimeMs
    ) {}
    
    // ==================== BACKTRACKING: VIAJES POR PRESUPUESTO ====================
    
    public BacktrackingBudgetDto findTripsWithinBudget(String startCity, double budget, 
                                                       Integer maxCities, Boolean returnToOrigin) {
        long startTime = System.nanoTime();
        System.out.println("🎒 Backtracking: Buscando viajes desde " + startCity + " con presupuesto €" + budget);
        
        // Verificar que la ciudad de inicio exista
        List<Station> startStations = repo.findStationsByCity(startCity);
        if (startStations.isEmpty()) {
            return new BacktrackingBudgetDto(
                startCity,
                budget,
                0,
                List.of(),
                "Ciudad no encontrada: " + startCity,
                0.0
            );
        }
        
        // Obtener todas las ciudades disponibles
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
        
        // Preparar estructuras para backtracking
        List<TripOption> validTrips = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        List<String> currentPath = new ArrayList<>();
        
        visited.add(startCity);
        currentPath.add(startCity);
        
        // Limitar a 4 ciudades por defecto para evitar explosión combinatoria
        int maxCitiesLimit = maxCities != null ? maxCities : Math.min(4, allCities.size());
        boolean mustReturn = returnToOrigin != null && returnToOrigin;
        
        System.out.println("🔍 Iniciando backtracking (max ciudades: " + maxCitiesLimit + ", retorno: " + mustReturn + ")");
        
        // Pre-calcular matriz de conectividad entre ciudades para optimizar
        System.out.println("📊 Pre-calculando rutas entre ciudades...");
        Map<String, Map<String, GreedyPathDto>> cityConnectionCache = new HashMap<>();
        
        for (String city1 : allCities) {
            cityConnectionCache.put(city1, new HashMap<>());
            for (String city2 : allCities) {
                if (!city1.equals(city2)) {
                    GreedyPathDto path = greedyPathByCity(city1, city2, "price", null, null, null);
                    if (!path.path().isEmpty() && path.totalPrice() != null) {
                        cityConnectionCache.get(city1).put(city2, path);
                    }
                }
            }
            System.out.println("  ✓ Rutas desde " + city1 + ": " + cityConnectionCache.get(city1).size());
        }
        
        System.out.println("✅ Rutas pre-calculadas. Iniciando exploración...");
        
        // Ejecutar backtracking
        int[] explorationCount = {0}; // Contador de exploraciones
        backtrackTrips(startCity, startCity, currentPath, visited, 0.0, 0.0, 0.0, 
                      budget, maxCitiesLimit, mustReturn, validTrips, new ArrayList<>(), 
                      cityConnectionCache, explorationCount);
        
        // Ordenar por número de ciudades (desc) y luego por precio (asc)
        validTrips.sort((t1, t2) -> {
            int citiesCompare = Integer.compare(t2.citiesVisited, t1.citiesVisited);
            if (citiesCompare != 0) return citiesCompare;
            return Double.compare(t1.totalPrice, t2.totalPrice);
        });
        
        long endTime = System.nanoTime();
        double executionTimeMs = (endTime - startTime) / 1_000_000.0;
        
        System.out.println("✅ Backtracking completado: " + validTrips.size() + " viajes encontrados");
        System.out.println("📊 Nodos explorados: " + explorationCount[0]);
        System.out.println("⏱️ Tiempo total: " + executionTimeMs + " ms");
        
        return new BacktrackingBudgetDto(
            startCity,
            budget,
            validTrips.size(),
            validTrips,
            validTrips.isEmpty() ? "No se encontraron viajes dentro del presupuesto" : 
                                   "Encontrados " + validTrips.size() + " viajes posibles",
            executionTimeMs
        );
    }
    
    private void backtrackTrips(String startCity, String currentCity, List<String> currentPath,
                                Set<String> visited, double currentPrice, double currentDuration, 
                                double currentDistance, double budget, int maxCities,
                                boolean returnToOrigin, List<TripOption> validTrips,
                                List<BacktrackSegment> currentSegments,
                                Map<String, Map<String, GreedyPathDto>> cityConnectionCache,
                                int[] explorationCount) {
        
        explorationCount[0]++;
        if (explorationCount[0] % 100 == 0) {
            System.out.println("  🔄 Exploraciones: " + explorationCount[0] + ", Viajes encontrados: " + validTrips.size());
        }
        
        // Si ya tenemos un viaje válido (más de 1 ciudad), guardarlo
        if (currentPath.size() > 1) {
            // Si debe retornar al origen y estamos de vuelta, o si no necesita retornar
            if ((returnToOrigin && currentCity.equals(startCity)) || !returnToOrigin) {
                validTrips.add(new TripOption(
                    new ArrayList<>(currentPath),
                    currentPath.size(),
                    currentPrice,
                    currentDuration,
                    currentDistance,
                    new ArrayList<>(currentSegments),
                    returnToOrigin && currentCity.equals(startCity)
                ));
            }
        }
        
        // Límite de profundidad alcanzado
        if (currentPath.size() >= maxCities) {
            // Si debe retornar al origen, intentar volver
            if (returnToOrigin && !currentCity.equals(startCity)) {
                tryReturnToOrigin(startCity, currentCity, currentPath, currentPrice, 
                                currentDuration, currentDistance, budget, validTrips, currentSegments,
                                cityConnectionCache);
            }
            return;
        }
        
        // Obtener ciudades alcanzables desde el caché
        Map<String, GreedyPathDto> reachableCities = cityConnectionCache.get(currentCity);
        if (reachableCities == null || reachableCities.isEmpty()) {
            return;
        }
        
        // Explorar cada ciudad alcanzable
        for (Map.Entry<String, GreedyPathDto> entry : reachableCities.entrySet()) {
            String nextCity = entry.getKey();
            GreedyPathDto pathToNext = entry.getValue();
            
            // No visitar ciudades ya visitadas (excepto si es el origen y queremos volver)
            if (visited.contains(nextCity) && !(returnToOrigin && nextCity.equals(startCity) && currentPath.size() > 2)) {
                continue;
            }
            
            double newPrice = currentPrice + pathToNext.totalPrice();
            double newDuration = currentDuration + (pathToNext.totalDuration() != null ? pathToNext.totalDuration() : 0);
            double newDistance = currentDistance + (pathToNext.totalDistance() != null ? pathToNext.totalDistance() : 0);
            
            // Verificar que no exceda el presupuesto
            if (newPrice > budget) {
                continue;
            }
            
            // Si es el retorno al origen, es un caso especial
            if (nextCity.equals(startCity) && returnToOrigin) {
                // Solo permitir volver si hemos visitado al menos 2 ciudades (origen + 1 más)
                if (currentPath.size() >= 2) {
                    currentPath.add(nextCity);
                    List<BacktrackSegment> newSegments = new ArrayList<>(currentSegments);
                    newSegments.add(new BacktrackSegment(
                        currentCity,
                        nextCity,
                        pathToNext.segments(),
                        pathToNext.totalPrice()
                    ));
                    
                    validTrips.add(new TripOption(
                        new ArrayList<>(currentPath),
                        currentPath.size() - 1, // No contar el retorno como ciudad adicional
                        newPrice,
                        newDuration,
                        newDistance,
                        newSegments,
                        true
                    ));
                    
                    currentPath.remove(currentPath.size() - 1);
                }
                continue;
            }
            
            // Hacer la elección (CHOOSE)
            visited.add(nextCity);
            currentPath.add(nextCity);
            List<BacktrackSegment> newSegments = new ArrayList<>(currentSegments);
            newSegments.add(new BacktrackSegment(
                currentCity,
                nextCity,
                pathToNext.segments(),
                pathToNext.totalPrice()
            ));
            
            // Explorar recursivamente (EXPLORE)
            backtrackTrips(startCity, nextCity, currentPath, visited, newPrice, newDuration,
                          newDistance, budget, maxCities, returnToOrigin, validTrips, newSegments,
                          cityConnectionCache, explorationCount);
            
            // Deshacer la elección (UNCHOOSE) - BACKTRACK
            currentPath.remove(currentPath.size() - 1);
            visited.remove(nextCity);
        }
    }
    
    private void tryReturnToOrigin(String startCity, String currentCity, List<String> currentPath,
                                   double currentPrice, double currentDuration, double currentDistance,
                                   double budget, List<TripOption> validTrips, List<BacktrackSegment> currentSegments,
                                   Map<String, Map<String, GreedyPathDto>> cityConnectionCache) {
        GreedyPathDto returnPath = cityConnectionCache.get(currentCity) != null ? 
                                   cityConnectionCache.get(currentCity).get(startCity) : null;
        
        if (returnPath == null) return;
        
        double totalPrice = currentPrice + returnPath.totalPrice();
        double totalDuration = currentDuration + (returnPath.totalDuration() != null ? returnPath.totalDuration() : 0);
        double totalDistance = currentDistance + (returnPath.totalDistance() != null ? returnPath.totalDistance() : 0);
        
        if (totalPrice <= budget) {
            List<String> pathWithReturn = new ArrayList<>(currentPath);
            pathWithReturn.add(startCity);
            
            List<BacktrackSegment> segmentsWithReturn = new ArrayList<>(currentSegments);
            segmentsWithReturn.add(new BacktrackSegment(
                currentCity,
                startCity,
                returnPath.segments(),
                returnPath.totalPrice()
            ));
            
            validTrips.add(new TripOption(
                pathWithReturn,
                currentPath.size(), // No contar el retorno
                totalPrice,
                totalDuration,
                totalDistance,
                segmentsWithReturn,
                true
            ));
        }
    }
    
    // DTOs para backtracking
    public record TripOption(
        List<String> route,
        int citiesVisited,
        double totalPrice,
        double totalDuration,
        double totalDistance,
        List<BacktrackSegment> segments,
        boolean completedLoop
    ) {}
    
    public record BacktrackSegment(
        String fromCity,
        String toCity,
        List<TripSegment> connections,
        double segmentCost
    ) {}
    
    public record BacktrackingBudgetDto(
        String startCity,
        double budget,
        int totalTripsFound,
        List<TripOption> trips,
        String message,
        double executionTimeMs
    ) {}
    
    // ==================== DTOs ====================
    public record PathDto(List<String> path, int hops, List<String> visited, double totalDistance) {}

    // DTO para representar un segmento del viaje
    public record TripSegment(
        String from,           // Código de origen
        String fromName,       // Nombre de origen
        String to,             // Código de destino
        String toName,         // Nombre de destino
        String mode,           // Modo de transporte (air, train, bus)
        String carrier,        // Compañía (Vueling, RENFE, etc.)
        Double price,          // Precio del segmento
        Double duration,       // Duración del segmento
        Double distance        // Distancia del segmento
    ) {}

    public record GreedyPathDto(
        List<String> path,           // Lista de códigos de estaciones
        int hops,                    // Número de saltos
        List<String> visited,        // Nodos visitados durante la búsqueda
        List<TripSegment> segments,  // 🚂 Información detallada de cada tramo
        Double totalPrice,           // Precio total real
        Double totalDuration,        // Duración total real
        Double totalDistance,        // Distancia total real
        String criterion             // Criterio usado
    ) {}

   public record MSTEdge(String from, String to, double weight) {}
    public record MSTDto(List<MSTEdge> edges, double totalWeight, int nodeCount, String message) {}         
}