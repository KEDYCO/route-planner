package ar.edu.uade.route_planner.service;

import ar.edu.uade.route_planner.service.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ═══════════════════════════════════════════════════════════════
 * ALGORITMO: DIVIDE Y VENCERÁS (DIVIDE & CONQUER)
 * ═══════════════════════════════════════════════════════════════
 * Servicio para catálogo de estaciones usando algoritmos de ordenamiento
 * 
 * ALGORITMOS IMPLEMENTADOS:
 * - QUICKSORT: Divide y vencerás con pivote
 * - MERGESORT: Divide y vencerás con fusión
 * 
 * Ambos dividen el problema en subproblemas más pequeños
 * ═══════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
public class CatalogService {
    private final Neo4jClient neo4jClient;

    /**
     * Obtiene un catálogo completo de estaciones organizadas por país.
     * 
     * FLUJO:
     * 1. Consulta la base de datos Neo4j para obtener todas las estaciones
     * 2. Agrupa las estaciones por país
     * 3. Ordena las estaciones dentro de cada país (usando quicksort o mergesort)
     * 4. Separa las estaciones en aeropuertos y estaciones de tren
     * 5. Retorna un catálogo completo con estadísticas y los mejores aeropuertos
     * 
     * @param sortAlgorithm el algoritmo a usar: "quicksort" o "mergesort"
     * @return CatalogDto con toda la información organizada
     */
    public CatalogDto getCatalog(String sortAlgorithm) {
        // Registra el tiempo de inicio para medir cuánto tarda el ordenamiento
        long startTime = System.nanoTime();
        
        // Ejecuta una consulta en Neo4j para obtener todas las estaciones
        // - MATCH (s:Station): busca todos los nodos de tipo Station
        // - IN_CITY: obtiene la ciudad a la que pertenece cada estación
        // - OPTIONAL MATCH para conexiones: cuenta cuántas conexiones tiene cada estación
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
            // Mapea cada registro de la base de datos a un objeto StationInfo
            // Convierte los valores a string, manejando nulos (si no existe un valor, usa null)
            .map(record -> {
                // Extrae el código de la estación, convirtiéndolo a string de forma segura
                String stationCode = record.get("stationCode") != null ? record.get("stationCode").toString() : null;
                // Extrae el nombre de la estación
                String stationName = record.get("stationName") != null ? record.get("stationName").toString() : null;
                // Extrae el tipo de estación (AIRPORT o TRAIN)
                String stationType = record.get("stationType") != null ? record.get("stationType").toString() : null;
                // Extrae el código de la ciudad
                String cityCode = record.get("cityCode") != null ? record.get("cityCode").toString() : null;
                // Extrae el nombre de la ciudad
                String cityName = record.get("cityName") != null ? record.get("cityName").toString() : null;
                // Extrae el país (información clave para agrupar después)
                String country = record.get("country") != null ? record.get("country").toString() : null;
                // Convierte el conteo de conexiones a un número entero (por defecto 0 si no hay)
                // connectionCount es usado luego para ordenar estaciones por importancia
                Integer connectionCount = record.get("connectionCount") instanceof Number ? 
                    ((Number) record.get("connectionCount")).intValue() : 0;
                
                // Crea y retorna un objeto StationInfo con todos los datos extraídos
                return new StationInfo(stationCode, stationName, stationType, cityCode, cityName, country, connectionCount);
            })
            .toList(); // Convierte el stream en una lista
        
        // Agrupa las estaciones por país usando un TreeMap (mantiene orden alfabético)
        // Esto permite organizarlas por país después
        Map<String, List<StationInfo>> byCountry = new TreeMap<>();
        for (StationInfo station : stationList) {
            // Para cada estación, agrégala a su país correspondiente
            // Si el país no existe aún en el mapa, crea una nueva lista
            byCountry.computeIfAbsent(station.country(), k -> new ArrayList<>()).add(station);
        }
        
        // Crea una lista de objetos CountryData (información por país)
        List<CountryData> countries = new ArrayList<>();
        // Itera sobre cada país y sus estaciones asociadas
        for (Map.Entry<String, List<StationInfo>> entry : byCountry.entrySet()) {
            // Crea una copia de la lista de estaciones del país para ordenarla
            List<StationInfo> countryStations = new ArrayList<>(entry.getValue());
            
            // Elige el algoritmo de ordenamiento según el parámetro pasado
            // Ordena las estaciones por número de conexiones (descendente)
            if ("mergesort".equalsIgnoreCase(sortAlgorithm)) {
                // Si se solicitó mergesort, lo usa
                mergeSort(countryStations, 0, countryStations.size() - 1);
            } else {
                // Por defecto usa quicksort (más rápido en promedio)
                quickSort(countryStations, 0, countryStations.size() - 1);
            }
            
            // Filtra solo los aeropuertos del país
            List<StationInfo> airports = countryStations.stream()
                .filter(s -> "AIRPORT".equals(s.type()))
                .toList();
            // Filtra solo las estaciones de tren del país
            List<StationInfo> trains = countryStations.stream()
                .filter(s -> "TRAIN".equals(s.type()))
                .toList();
            
            // Obtiene todas las ciudades únicas del país (sin duplicados, gracias a Set)
            Set<String> cities = countryStations.stream()
                .map(StationInfo::cityName)  // Extrae el nombre de la ciudad de cada estación
                .collect(Collectors.toSet()); // Convierte a Set para eliminar duplicados
            
            countries.add(new CountryData(entry.getKey(), new ArrayList<>(cities), airports, trains));
        }
        
        List<StationInfo> topStations = new ArrayList<>(stationList);
        if ("mergesort".equalsIgnoreCase(sortAlgorithm)) {
            mergeSort(topStations, 0, topStations.size() - 1);
        } else {
            quickSort(topStations, 0, topStations.size() - 1);
        }
        
        List<StationInfo> topAirports = topStations.stream()
            .filter(s -> "AIRPORT".equals(s.type()))
            .limit(5)
            .toList();
        
        long endTime = System.nanoTime();
        double sortingTimeMs = (endTime - startTime) / 1_000_000.0;
        
        long totalCountries = byCountry.size();
        long totalCities = stationList.stream().map(StationInfo::cityName).distinct().count();
        long totalStations = stationList.size();
        long totalAirports = stationList.stream().filter(s -> "AIRPORT".equals(s.type())).count();
        long totalTrains = stationList.stream().filter(s -> "TRAIN".equals(s.type())).count();
        
        CatalogStats stats = new CatalogStats(
            (int) totalCountries,
            (int) totalCities,
            (int) totalStations,
            (int) totalAirports,
            (int) totalTrains
        );
        
        return new CatalogDto(stats, countries, topAirports, sortAlgorithm, sortingTimeMs);
    }
    
    // ==================== QUICKSORT (DIVIDE Y VENCERÁS) ====================
    /**
     * QUICKSORT: Ordena una lista usando el método de divide y vencerás.
     * 
     * CÓMO FUNCIONA:
     * 1. Elige un pivote (el último elemento)
     * 2. Particiona la lista: elementos >= pivote a la izquierda, menores a la derecha
     * 3. Ordena recursivamente la parte izquierda y derecha
     * 
     * VENTAJAS: Muy rápido en promedio (O(n log n)), usa poco espacio extra
     * DESVENTAJAS: En casos extremos puede ser lento (O(n²))
     * 
     * @param list lista de estaciones a ordenar
     * @param low índice del inicio del rango a ordenar
     * @param high índice del final del rango a ordenar
     */
    private void quickSort(List<StationInfo> list, int low, int high) {
        // Si hay más de un elemento en el rango (low < high)
        if (low < high) {
            // Particiona la lista y obtiene el índice del pivote ordenado
            int pi = partition(list, low, high);
            // Ordena recursivamente la parte izquierda del pivote
            quickSort(list, low, pi - 1);
            // Ordena recursivamente la parte derecha del pivote
            quickSort(list, pi + 1, high);
        }
    }
    
    /**
     * PARTICIÓN (parte crucial de Quicksort):
     * Reorganiza la lista para que elementos >= pivote queden a la izquierda
     * y elementos < pivote queden a la derecha.
     * 
     * RESULTADO: Devuelve la posición final del pivote tras la reorganización.
     * Después de particionar, el pivote está en su posición final ordenada.
     * 
     * @param list lista de estaciones
     * @param low inicio del rango a particionar
     * @param high fin del rango (aquí está el pivote)
     * @return índice de la posición final del pivote
     */
    private int partition(List<StationInfo> list, int low, int high) {
        // El pivote es el número de conexiones del último elemento (high)
        // Se usa esto para dividir la lista: elementos con >= conexiones van adelante
        int pivot = list.get(high).connectionCount();
        // i marca la frontera entre elementos >= pivote (a la izquierda) y el resto
        int i = low - 1;
        
        // Itera desde low hasta high-1 (no incluye el pivote aún)
        for (int j = low; j < high; j++) {
            // Si el número de conexiones es >= al pivote
            if (list.get(j).connectionCount() >= pivot) {
                // Incrementa i (expande la zona de elementos >= pivote)
                i++;
                // Intercambia: trae este elemento a la zona izquierda
                // Esta es una operación de SWAP (intercambio de elementos)
                StationInfo temp = list.get(i);
                list.set(i, list.get(j));
                list.set(j, temp);
            }
        }
        
        // Finalmente, coloca el pivote en su posición final (i+1)
        // Esto asegura que todo a su izquierda >= pivote, y todo a la derecha < pivote
        StationInfo temp = list.get(i + 1);
        list.set(i + 1, list.get(high));
        list.set(high, temp);
        
        // Devuelve la posición final del pivote
        return i + 1;
    }
    
    // ==================== MERGESORT (DIVIDE Y VENCERÁS) ====================
    /**
     * MERGESORT: Ordena una lista dividiendo en mitades y fusionando.
     * 
     * CÓMO FUNCIONA:
     * 1. Divide la lista en dos mitades recursivamente hasta tener elementos individuales
     * 2. Fusiona las mitades ordenadas (esto es lo inteligente de mergesort)
     * 3. Resultado: una lista completamente ordenada
     * 
     * VENTAJAS: Muy predecible, SIEMPRE O(n log n), estable (mantiene orden relativo)
     * DESVENTAJAS: Usa espacio extra O(n), un poco más lento que quicksort en promedio
     * 
     * @param list lista de estaciones a ordenar
     * @param left índice del inicio del rango
     * @param right índice del final del rango
     */
    private void mergeSort(List<StationInfo> list, int left, int right) {
        // Si hay más de un elemento (left < right)
        if (left < right) {
            // Calcula el punto medio (así evita overflow)
            int mid = left + (right - left) / 2;
            
            // Divide y conquista: ordena la mitad izquierda
            mergeSort(list, left, mid);
            // Divide y conquista: ordena la mitad derecha
            mergeSort(list, mid + 1, right);
            
            // Fusiona las dos mitades ordenadas en una lista ordenada completa
            merge(list, left, mid, right);
        }
    }
    
    /**
     * MERGE (fusión): Combina dos mitades ordenadas en una lista completamente ordenada.
     * 
     * LÓGICA: Compara elementos de ambas mitades y agrega el mayor primero
     * (así las estaciones más conectadas quedan adelante).
     * 
     * @param list lista original
     * @param left inicio de la primera mitad
     * @param mid fin de la primera mitad y punto de division
     * @param right fin de la segunda mitad
     */
    private void merge(List<StationInfo> list, int left, int mid, int right) {
        // Calcula el tamaño de la mitad izquierda
        int n1 = mid - left + 1;
        // Calcula el tamaño de la mitad derecha
        int n2 = right - mid;
        
        // Crea dos listas temporales para almacenar las dos mitades
        List<StationInfo> leftArray = new ArrayList<>(n1);
        List<StationInfo> rightArray = new ArrayList<>(n2);
        
        // Copia la mitad izquierda en leftArray
        for (int i = 0; i < n1; i++) {
            leftArray.add(list.get(left + i));
        }
        // Copia la mitad derecha en rightArray
        for (int j = 0; j < n2; j++) {
            rightArray.add(list.get(mid + 1 + j));
        }
        
        // Índices para recorrer leftArray, rightArray y la lista original
        int i = 0, j = 0, k = left;
        
        // FUSIÓN: Compara elementos de ambas mitades
        // Agrega el elemento con MÁS CONEXIONES primero (orden descendente)
        while (i < n1 && j < n2) {
            // Si el elemento izquierdo tiene >= conexiones que el derecho
            if (leftArray.get(i).connectionCount() >= rightArray.get(j).connectionCount()) {
                // Agrega el elemento izquierdo en su lugar ordenado
                list.set(k, leftArray.get(i));
                i++; // Avanza en la mitad izquierda
            } else {
                // En caso contrario, agrega el elemento derecho
                list.set(k, rightArray.get(j));
                j++; // Avanza en la mitad derecha
            }
            k++; // Avanza en la lista original
        }
        
        // Si quedan elementos en la mitad izquierda, agrégalos al final
        // (la mitad derecha ya está agotada, no hay que compararlos más)
        while (i < n1) {
            list.set(k, leftArray.get(i));
            i++;
            k++;
        }
        
        // Si quedan elementos en la mitad derecha, agrégalos al final
        while (j < n2) {
            list.set(k, rightArray.get(j));
            j++;
            k++;
        }
    }
}

