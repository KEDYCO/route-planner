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

    public CatalogDto getCatalog(String sortAlgorithm) {
        long startTime = System.nanoTime();
        
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
        
        Map<String, List<StationInfo>> byCountry = new TreeMap<>();
        for (StationInfo station : stationList) {
            byCountry.computeIfAbsent(station.country(), k -> new ArrayList<>()).add(station);
        }
        
        List<CountryData> countries = new ArrayList<>();
        for (Map.Entry<String, List<StationInfo>> entry : byCountry.entrySet()) {
            List<StationInfo> countryStations = new ArrayList<>(entry.getValue());
            
            if ("mergesort".equalsIgnoreCase(sortAlgorithm)) {
                mergeSort(countryStations, 0, countryStations.size() - 1);
            } else {
                quickSort(countryStations, 0, countryStations.size() - 1);
            }
            
            List<StationInfo> airports = countryStations.stream()
                .filter(s -> "AIRPORT".equals(s.type()))
                .toList();
            List<StationInfo> trains = countryStations.stream()
                .filter(s -> "TRAIN".equals(s.type()))
                .toList();
            
            Set<String> cities = countryStations.stream()
                .map(StationInfo::cityName)
                .collect(Collectors.toSet());
            
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
    // ALGORITMO: QUICKSORT - Divide usando pivote, ordena recursivamente
    private void quickSort(List<StationInfo> list, int low, int high) {
        if (low < high) {
            int pi = partition(list, low, high);
            quickSort(list, low, pi - 1);
            quickSort(list, pi + 1, high);
        }
    }
    
    private int partition(List<StationInfo> list, int low, int high) {
        int pivot = list.get(high).connectionCount();
        int i = low - 1;
        
        for (int j = low; j < high; j++) {
            if (list.get(j).connectionCount() >= pivot) {
                i++;
                StationInfo temp = list.get(i);
                list.set(i, list.get(j));
                list.set(j, temp);
            }
        }
        
        StationInfo temp = list.get(i + 1);
        list.set(i + 1, list.get(high));
        list.set(high, temp);
        
        return i + 1;
    }
    
    // ==================== MERGESORT (DIVIDE Y VENCERÁS) ====================
    // ALGORITMO: MERGESORT - Divide en mitades, ordena y fusiona
    private void mergeSort(List<StationInfo> list, int left, int right) {
        if (left < right) {
            int mid = left + (right - left) / 2;
            
            mergeSort(list, left, mid);
            mergeSort(list, mid + 1, right);
            
            merge(list, left, mid, right);
        }
    }
    
    private void merge(List<StationInfo> list, int left, int mid, int right) {
        int n1 = mid - left + 1;
        int n2 = right - mid;
        
        List<StationInfo> leftArray = new ArrayList<>(n1);
        List<StationInfo> rightArray = new ArrayList<>(n2);
        
        for (int i = 0; i < n1; i++) {
            leftArray.add(list.get(left + i));
        }
        for (int j = 0; j < n2; j++) {
            rightArray.add(list.get(mid + 1 + j));
        }
        
        int i = 0, j = 0, k = left;
        
        while (i < n1 && j < n2) {
            if (leftArray.get(i).connectionCount() >= rightArray.get(j).connectionCount()) {
                list.set(k, leftArray.get(i));
                i++;
            } else {
                list.set(k, rightArray.get(j));
                j++;
            }
            k++;
        }
        
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
}

