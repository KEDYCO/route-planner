package ar.edu.uade.route_planner.domain;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("City")
public class City {
    @Id public String code;
    public String name, country;
    public Double lat, lon;
}
