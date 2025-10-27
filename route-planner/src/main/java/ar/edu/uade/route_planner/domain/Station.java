package ar.edu.uade.route_planner.domain;
import java.util.List;
import org.springframework.data.neo4j.core.schema.*;

@Node("Station")
public class Station {
    @Id public String code;
    public String name;
    public String type; // AIRPORT / BUS / TRAIN

    @Relationship(type="IN_CITY", direction = Relationship.Direction.OUTGOING)
    public City city;

    @Relationship(type="CONNECTS", direction = Relationship.Direction.OUTGOING)
    public List<Connection> edges;
}
