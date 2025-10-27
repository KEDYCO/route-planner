package ar.edu.uade.route_planner.repo;

import ar.edu.uade.route_planner.domain.Station;
import java.util.List;
import java.util.Map;
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

    // (Opcional) Chequeo rápido de reachability en <=6 saltos
    @Query("""
    MATCH (a:Station {code:$from}), (b:Station {code:$to})
    RETURN exists( (a)-[:CONNECTS*..6]->(b) ) as reachable
  """)
    Boolean reachable(String from, String to);

    // (Opcional) Cargar todas las aristas si querés armar el grafo en memoria
    @Query("""
    MATCH (s:Station)-[e:CONNECTS]->(t:Station)
    RETURN s.code as from, t.code as to,
           {mode:e.mode, carrier:e.carrier, price:e.price, duration:e.duration,
            dist:e.dist, freq:e.freq, overnight:e.overnight} as edge
  """)
    List<Map<String,Object>> allEdges();
}
