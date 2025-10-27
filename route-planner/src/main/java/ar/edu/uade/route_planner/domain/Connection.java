package ar.edu.uade.route_planner.domain;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

@RelationshipProperties
public class Connection {

    @Id
    @GeneratedValue
    private Long id;                // 👈 ID interno requerido por SDN 7

    @TargetNode
    public Station to;

    public String mode, carrier;
    public Double price, duration, dist;
    public Integer freq;
    public Boolean overnight;

    // (Opcional) getters/setters o Lombok (@Data) si querés
}
