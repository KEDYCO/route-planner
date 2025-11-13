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
    /**
     * Busca la mejor ruta greedy ENTRE CIUDADES (no entre estaciones específicas).
     * 
     * ALGORITMO GREEDY:
     * - En cada paso, elige la mejor opción local según el criterio (precio, duración, distancia)
     * - No garantiza la solución global óptima, pero es eficiente
     * - Prueba todas las combinaciones de estaciones ciudad-a-ciudad y devuelve la mejor
     * 
     * @param fromCity ciudad de origen (puede tener múltiples estaciones)
     * @param toCity ciudad destino (puede tener múltiples estaciones)
     * @param criterion criterio de optimización: "price", "duration", "distance", "balanced", "custom"
     * @param priceWeight peso del precio (solo si criterion="custom")
     * @param durationWeight peso de la duración (solo si criterion="custom")
     * @param distWeight peso de la distancia (solo si criterion="custom")
     * @return GreedyPathDto con la mejor ruta encontrada
     */
    public GreedyPathDto greedyPathByCity(String fromCity, String toCity, String criterion,
                                          Double priceWeight, Double durationWeight, Double distWeight) {
        // Obtiene todas las estaciones en la ciudad de origen
        List<Station> fromStations = repo.findStationsByCity(fromCity);
        // Obtiene todas las estaciones en la ciudad destino
        List<Station> toStations = repo.findStationsByCity(toCity);
        
        // Validación: si no hay estaciones en la ciudad de origen, no hay ruta
        if (fromStations.isEmpty()) {
            return new GreedyPathDto(List.of(), -1, List.of(), List.of(), null, null, null, criterion);
        }
        
        // Validación: si no hay estaciones en la ciudad destino, no hay ruta
        if (toStations.isEmpty()) {
            return new GreedyPathDto(List.of(), -1, List.of(), List.of(), null, null, null, criterion);
        }
        
        // Variable para guardar la mejor ruta encontrada hasta ahora
        GreedyPathDto bestPath = null;
        // Variable para guardar el costo de la mejor ruta (inicializado a infinito)
        double bestCost = Double.POSITIVE_INFINITY;
        
        // Determina si el usuario proporcionó pesos personalizados
        boolean hasCustomWeights = priceWeight != null || durationWeight != null || distWeight != null;
        // Define el criterio a usar: si hay pesos personalizados, usa "custom", si no, usa el criterio pasado
        String criterionLower = hasCustomWeights ? "custom" : 
                               (criterion != null ? criterion.toLowerCase() : "combined");
        
        // BÚSQUEDA EXHAUSTIVA: Prueba todas las combinaciones de estaciones
        // (estación origen X estación destino) para encontrar la mejor ruta
        for (Station fromStation : fromStations) {
            for (Station toStation : toStations) {
                // Ejecuta el algoritmo greedy entre estas dos estaciones específicas
                GreedyPathDto path = greedyPath(fromStation.code, toStation.code, criterionLower, 
                                               priceWeight, durationWeight, distWeight);
                
                // Si no hay ruta válida entre estas estaciones, continúa con la siguiente
                if (path.path().isEmpty()) continue;
                
                // GREEDY SELECTION: Calcula el costo total de esta ruta según el criterio
                double cost = calculatePathCost(path, criterionLower, priceWeight, durationWeight, distWeight);
                
                // Si esta ruta es MEJOR (menor costo) que la mejor anterior, la guarда
                if (cost < bestCost) {
                    bestCost = cost;
                    bestPath = path;
                }
            }
        }
        
        // Si se encontró una ruta y se usaron pesos personalizados, retorna con etiqueta "custom"
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
        
        // Retorna la mejor ruta encontrada, o una ruta vacía si no se encontró ninguna
        return bestPath != null ? bestPath : 
            new GreedyPathDto(List.of(), -1, List.of(), List.of(), null, null, null, criterionLower);
    }

    // ==================== GREEDY MULTI-CRITERIA ====================
    /**
     * ALGORITMO GREEDY (CODICIOSO) - Búsqueda de ruta con múltiples criterios.
     * 
     * CÓMO FUNCIONA:
     * 1. Inicializa una PriorityQueue (cola de prioridad) con el nodo inicial
     * 2. En cada iteración, elige el nodo NO VISITADO con MENOR COSTO (greedy step)
     * 3. Calcula el costo combinado de cada arista según los pesos del criterio:
     *    - Si criterion="price": solo optimiza precio
     *    - Si criterion="duration": solo optimiza duración
     *    - Si criterion="distance": solo optimiza distancia
     *    - Si criterion="balanced" o "custom": combina los tres con pesos personalizados
     * 4. Si encuenttra el destino, reconstruye y retorna la ruta
     * 
     * NOTA: A diferencia de Dijkstra, GREEDY SELECCIONA LA MEJOR OPCIÓN LOCAL sin garantizar
     * que será el camino global más corto (aunque a menudo lo es si la función de costo es correcta).
     * 
     * @param from estación de origen (código)
     * @param to estación destino (código)
     * @param criterion "price", "duration", "distance", "balanced", o "custom"
     * @param priceWeight peso del precio (solo si criterion="custom")
     * @param durationWeight peso de la duración (solo si criterion="custom")
     * @param distWeight peso de la distancia (solo si criterion="custom")
     * @return GreedyPathDto con la ruta encontrada y estadísticas
     */
    public GreedyPathDto greedyPath(String from, String to, String criterion, 
                              Double priceWeight, Double durationWeight, Double distWeight) {
        // Caso base: si origen = destino, ya estamos ahí
        if (Objects.equals(from, to)) {
            return new GreedyPathDto(List.of(from), 0, List.of(from), List.of(), 0.0, 0.0, 0.0, criterion);
        }

        // Si no se proporcionan pesos, asigna los pesos por defecto según el criterio
        // Esto es CRUCIAL para determinar cómo se pondera cada factor en la decisión greedy
        if (priceWeight == null || durationWeight == null || distWeight == null) {
            double[] weights = getDefaultWeights(criterion);
            priceWeight = weights[0];
            durationWeight = weights[1];
            distWeight = weights[2];
        }

        // ESTRUCTURA DE DATOS PARA EL ALGORITMO:
        // - cost: almacena el costo acumulado para cada nodo desde el origen
        // - parent: almacena el nodo anterior en la ruta (para reconstruir el camino)
        // - edgeUsed: almacena la conexión usada para llegar a cada nodo (para obtener detalles de viaje)
        // - visited: conjunto de nodos ya procesados (no se procesan dos veces)
        // - pq: cola de prioridad que siempre devuelve el nodo con MENOR costo (GREEDY SELECTION)
        Map<String, Double> cost = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        Map<String, Connection> edgeUsed = new HashMap<>();
        Set<String> visited = new LinkedHashSet<>();
        PriorityQueue<NodeCost> pq = new PriorityQueue<>(Comparator.comparingDouble(nc -> nc.cost));

        // Inicialización: el costo desde 'from' a sí mismo es 0
        cost.put(from, 0.0);
        // Agrega el nodo inicial a la cola de prioridad
        pq.offer(new NodeCost(from, 0.0));

        // Guardar los pesos como constantes finales para usar en lambda
        final double fPriceW = priceWeight;
        final double fDurationW = durationWeight;
        final double fDistW = distWeight;

        // BUCLE PRINCIPAL DEL ALGORITMO GREEDY
        while (!pq.isEmpty()) {
            // *** PASO GREEDY CRUCIAL: Obtiene el nodo NO VISITADO con MENOR COSTO ***
            // Esto es lo que lo hace "greedy" - siempre elige la mejor opción local
            NodeCost current = pq.poll();
            String u = current.code;

            // Si ya visitamos este nodo, lo ignoramos (evita procesarlo dos veces)
            if (visited.contains(u)) continue;
            // Marca el nodo como visitado
            visited.add(u);

            // Si llegamos al destino, reconstruye y retorna la ruta
            if (u.equals(to)) {
                return buildGreedyPath(from, to, parent, edgeUsed, visited, criterion);
            }

            // Obtiene todas las conexiones (aristas) desde este nodo
            Station hop = repo.oneHop(u);
            if (hop == null || hop.edges == null) continue;

            // ITERACIÓN SOBRE VECINOS: Para cada arista saliente
            for (Connection c : hop.edges) {
                String v = c.to.code;
                // Si ya visitamos este vecino, lo ignoramos
                if (visited.contains(v)) continue;

                // *** CÁLCULO DEL COSTO GREEDY: Calcula el costo de esta arista según el criterio elegido ***
                // Este es el factor determinante en la selección greedy
                // Combina precio, duración y distancia según los pesos
                double edgeCost = calculateEdgeCost(c, fPriceW, fDurationW, fDistW);
                if (edgeCost == 0.0) continue;

                // Costo total para llegar a 'v' = costo acumulado en 'u' + costo de esta arista
                double newCost = cost.get(u) + edgeCost;

                // *** DECISIÓN GREEDY: Si esta ruta es mejor que la anterior conocida, actualiza ***
                // Si no hemos visitado 'v' o encontramos un costo menor, actualiza
                if (!cost.containsKey(v) || newCost < cost.get(v)) {
                    cost.put(v, newCost);               // Actualiza el mejor costo conocido
                    parent.put(v, u);                   // Registra el nodo anterior
                    edgeUsed.put(v, c);                 // Registra la arista usada
                    pq.offer(new NodeCost(v, newCost)); // Agrega a la cola para procesarlo después
                }
            }
        }

        // Si salimos del bucle sin encontrar destino, no hay ruta válida
        return new GreedyPathDto(List.of(), -1, new ArrayList<>(visited), List.of(), null, null, null, criterion);
    }

    // ==================== UTILIDADES PRIVADAS ====================
    
    /**
     * Calcula el costo TOTAL de una ruta completa según el criterio elegido.
     * 
     * Este método es usado para comparar rutas completas y seleccionar la mejor.
     * Combina precio, duración y distancia según el criterio:
     * - "price": solo precio
     * - "duration": solo duración
     * - "distance": solo distancia
     * - "balanced": precio 40%, duración 40%, distancia 20%
     * - "custom": pesos personalizados por el usuario
     * 
     * @param path ruta a evaluar
     * @param criterion tipo de criterio de evaluación
     * @param priceWeight peso del precio (personalizado)
     * @param durationWeight peso de la duración (personalizado)
     * @param distWeight peso de la distancia (personalizado)
     * @return costo total de la ruta
     */
    private double calculatePathCost(GreedyPathDto path, String criterion, 
                                    Double priceWeight, Double durationWeight, Double distWeight) {
        return switch (criterion.toLowerCase()) {
            // Criterio PRICE: solo optimiza precio
            case "price" -> path.totalPrice() != null ? path.totalPrice() : Double.POSITIVE_INFINITY;
            
            // Criterio DURATION: solo optimiza duración
            case "duration" -> path.totalDuration() != null ? path.totalDuration() : Double.POSITIVE_INFINITY;
            
            // Criterio DISTANCE: solo optimiza distancia
            case "distance" -> path.totalDistance() != null ? path.totalDistance() : Double.POSITIVE_INFINITY;
            
            // Criterio BALANCED: combina los tres con pesos predefinidos (40%-40%-20%)
            case "balanced" -> {
                double cost = 0.0;
                // Pesos por defecto si no se especificaron
                double pw = priceWeight != null ? priceWeight : 0.4;
                double dw = durationWeight != null ? durationWeight : 0.4;
                double distw = distWeight != null ? distWeight : 0.2;
                // Acumula el costo ponderado de cada factor
                // La duración se multiplica por 20 para ajustar la escala
                // La distancia se divide por 10 para normalizar
                if (path.totalPrice() != null) cost += pw * path.totalPrice();
                if (path.totalDuration() != null) cost += dw * path.totalDuration() * 20;
                if (path.totalDistance() != null) cost += distw * path.totalDistance() / 10;
                yield cost;
            }
            
            // Criterio CUSTOM: combina los tres con pesos del usuario
            case "custom" -> {
                double cost = 0.0;
                // Pesos personalizados del usuario (o 1/3 cada uno si no especificó)
                double cpw = priceWeight != null ? priceWeight : 0.33;
                double cdw = durationWeight != null ? durationWeight : 0.33;
                double cdistw = distWeight != null ? distWeight : 0.34;
                // Acumula el costo ponderado según los pesos del usuario
                if (path.totalPrice() != null) cost += cpw * path.totalPrice();
                if (path.totalDuration() != null) cost += cdw * path.totalDuration() * 20;
                if (path.totalDistance() != null) cost += cdistw * path.totalDistance() / 10;
                yield cost;
            }
            
            // Por defecto: combina los tres equitativamente
            default -> {
                double cost = 0.0;
                if (path.totalPrice() != null) cost += 0.33 * path.totalPrice();
                if (path.totalDuration() != null) cost += 0.33 * path.totalDuration() * 20;
                if (path.totalDistance() != null) cost += 0.34 * path.totalDistance() / 10;
                yield cost;
            }
        };
    }

    /**
     * Asigna los pesos por defecto (0-1) para cada factor según el criterio.
     * 
     * Estos pesos determinan cómo se calcula el costo de cada arista en el algoritmo greedy.
     * 
     * Por ejemplo:
     * - criterion="price" -> [1.0, 0.0, 0.0] (solo importa precio, ignorar duración y distancia)
     * - criterion="balanced" -> [0.4, 0.4, 0.2] (mezclar los tres)
     * - criterion="duration" -> [0.0, 1.0, 0.0] (solo importa duración)
     * 
     * @param criterion tipo de criterio elegido
     * @return array [precioWeight, durationWeight, distanceWeight]
     */
    private double[] getDefaultWeights(String criterion) {
        return switch (criterion.toLowerCase()) {
            // Optimizar SOLO precio: peso=1.0, otros=0.0
            case "price" -> new double[]{1.0, 0.0, 0.0};
            
            // Optimizar SOLO duración: peso=1.0, otros=0.0
            case "duration" -> new double[]{0.0, 1.0, 0.0};
            
            // Optimizar SOLO distancia: peso=1.0, otros=0.0
            case "distance" -> new double[]{0.0, 0.0, 1.0};
            
            // Equilibrio: precio 40%, duración 40%, distancia 20%
            case "balanced" -> new double[]{0.4, 0.4, 0.2};
            
            // Por defecto: distribución equitativa
            default -> new double[]{0.33, 0.33, 0.34};
        };
    }

    /**
     * Calcula el costo de UNA ARISTA (una conexión entre dos estaciones) según los pesos greedy.
     * 
     * Este es el FACTOR DETERMINANTE en la selección greedy - cada arista se evalúa
     * combinando precio, duración y distancia con los pesos especificados.
     * 
     * La selección greedy elige siempre la arista con MENOR COSTO calculado aquí.
     * 
     * Ejemplo:
     * - Si criterion="price" y una arista cuesta 50€:
     *   edgeCost = 1.0 * (50 / 100.0) = 0.5
     * - Si criterion="balanced" y priceWeight=0.4, la arista contribuye:
     *   edgeCost += 0.4 * (50 / 100.0) = 0.2
     * 
     * @param c conexión/arista a evaluar
     * @param priceW peso del precio (0-1)
     * @param durationW peso de la duración (0-1)
     * @param distW peso de la distancia (0-1)
     * @return costo normalizado de esta arista
     */
    private double calculateEdgeCost(Connection c, double priceW, double durationW, double distW) {
        double edgeCost = 0.0;
        
        // Componente PRECIO: si hay peso para precio, añade su contribución
        if (c.price != null && priceW > 0) {
            // Divide por 100 para normalizar (100€ = costo 1.0)
            edgeCost += priceW * (c.price / 100.0);
        }
        
        // Componente DURACIÓN: si hay peso para duración, añade su contribución
        if (c.duration != null && durationW > 0) {
            // Divide por 10 para normalizar (10 horas = costo 1.0)
            edgeCost += durationW * (c.duration / 10.0);
        }
        
        // Componente DISTANCIA: si hay peso para distancia, añade su contribución
        if (c.dist != null && distW > 0) {
            // Divide por 1000 para normalizar (1000 km = costo 1.0)
            edgeCost += distW * (c.dist / 1000.0);
        }
        
        // Retorna el costo NORMALIZADO de esta arista
        return edgeCost;
    }

    /**
     * Reconstruye la ruta completa a partir de los datos de búsqueda y calcula estadísticas.
     * 
     * Genera:
     * - path: lista de códigos de estaciones desde origen a destino
     * - segments: detalles de cada viaje (modo, transportista, precio, duración, etc.)
     * - totalPrice: suma de todos los precios
     * - totalDuration: suma de todas las duraciones
     * - totalDistance: suma de todas las distancias
     * 
     * @param from estación de origen
     * @param to estación destino
     * @param parent mapa padre (para reconstruir camino)
     * @param edgeUsed map de aristas usadas (para obtener detalles de viaje)
     * @param visited conjunto de nodos visitados
     * @param criterion criterio usado en la búsqueda
     * @return GreedyPathDto completo con ruta y estadísticas
     */
    private GreedyPathDto buildGreedyPath(String from, String to, Map<String, String> parent, 
                                         Map<String, Connection> edgeUsed, Set<String> visited, String criterion) {
        // Reconstruye el camino desde 'to' hacia atrás hasta 'from' usando el mapa parent
        List<String> path = reconstruct(parent, from, to);
        
        // Lista que contendrá los detalles de cada segmento de viaje
        List<TripSegment> segments = new ArrayList<>();
        
        // Acumuladores para estadísticas totales
        Double totalPrice = 0.0;
        Double totalDuration = 0.0;
        Double totalDistance = 0.0;
        
        // Itera sobre cada par consecutivo de estaciones en la ruta
        for (int i = 0; i < path.size() - 1; i++) {
            String fromNode = path.get(i);
            String toNode = path.get(i + 1);
            
            // Obtiene la conexión usada entre estas dos estaciones
            Connection conn = edgeUsed.get(toNode);
            
            if (conn != null) {
                // Obtiene información del nombre de la estación de origen
                Station fromStation = repo.oneHop(fromNode);
                String fromName = fromStation != null ? fromStation.name : fromNode;
                
                // Crea un segmento de viaje con todos los detalles
                TripSegment segment = new TripSegment(
                    fromNode, fromName, toNode, conn.to.name,
                    conn.mode, conn.carrier, conn.price, conn.duration, conn.dist
                );
                segments.add(segment);
                
                // Acumula los valores totales
                if (conn.price != null) totalPrice += conn.price;
                if (conn.duration != null) totalDuration += conn.duration;
                if (conn.dist != null) totalDistance += conn.dist;
            }
        }
        
        // Retorna el GreedyPathDto completo con ruta, estadísticas y detalles
        return new GreedyPathDto(
            path,                                      // Lista de códigos de estaciones
            path.size() - 1,                           // Número de saltos (tamaño - 1)
            new ArrayList<>(visited),                  // Nodos visitados en la búsqueda
            segments,                                  // Detalles de cada segmento
            totalPrice > 0 ? totalPrice : null,       // Precio total (null si no hay)
            totalDuration > 0 ? totalDuration : null, // Duración total (null si no hay)
            totalDistance > 0 ? totalDistance : null, // Distancia total (null si no hay)
            criterion                                  // Criterio usado
        );
    }

    /**
     * Reconstruye el camino desde el destino hacia el origen usando el mapa parent.
     * 
     * El algoritmo greedy almacena el "padre" de cada nodo visitado.
     * Este método sigue los padres hacia atrás desde 'to' hasta 'from'
     * para obtener la secuencia completa de estaciones.
     * 
     * @param parent mapa de nodo -> nodo_anterior
     * @param from estación de origen
     * @param to estación destino
     * @return lista de códigos de estaciones en orden (origen -> destino)
     */
    private List<String> reconstruct(Map<String, String> parent, String from, String to) {
        // LinkedList para agregar eficientemente al principio
        LinkedList<String> path = new LinkedList<>();
        
        // Comienza desde el destino e itera hacia atrás
        String cur = to;
        while (cur != null) {
            path.addFirst(cur); // Agrega al inicio (porque va hacia atrás)
            if (cur.equals(from)) break; // Llegamos al origen, termina
            cur = parent.get(cur); // Sigue al nodo anterior
        }
        
        // Valida que el camino empiece efectivamente en 'from'
        if (!path.isEmpty() && path.getFirst().equals(from)) {
            return new ArrayList<>(path);
        }
        
        // Si algo falla en la reconstrucción, retorna lista vacía
        return List.of();
    }

    private record NodeCost(String code, double cost) {}
}

