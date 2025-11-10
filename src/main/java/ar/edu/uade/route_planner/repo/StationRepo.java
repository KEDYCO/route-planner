package ar.edu.uade.route_planner.repo;

import ar.edu.uade.route_planner.domain.Station;
import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StationRepo extends Neo4jRepository<Station, String> {

    // Trae un nodo y sus aristas salientes (para expandir en BFS/Dijkstra)
    @Query("""
    MATCH (s:Station {code:$code})-[e:CONNECTS]->(t:Station)
    RETURN s, collect(e), collect(t)
    """)
    Station oneHop(String code);

    // Chequeo rápido de reachability en <=6 saltos
    @Query("""
    MATCH (a:Station {code:$from}), (b:Station {code:$to})
    RETURN exists( (a)-[:CONNECTS*..6]->(b) ) as reachable
    """)
    Boolean reachable(String from, String to);

    // MÉTODO CORREGIDO - Devuelve una proyección simple
    @Query("""
    MATCH (s:Station)-[e:CONNECTS]->(t:Station)
    WHERE e.dist IS NOT NULL
    RETURN s.code as from, t.code as to, e.dist as dist
    """)
    List<SimpleEdge> allEdgesSimple();
    
    // Obtener todas las estaciones de una ciudad
    @Query("""
    MATCH (s:Station)-[:IN_CITY]->(c:City {code:$cityCode})
    RETURN s
    """)
    List<Station> findStationsByCity(String cityCode);
    
    // Interfaz de proyección para el resultado
    interface SimpleEdge {
        String getFrom();
        String getTo();
        Double getDist();
    }
}