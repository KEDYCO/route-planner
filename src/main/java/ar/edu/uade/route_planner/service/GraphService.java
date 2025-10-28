package ar.edu.uade.route_planner.service;

import ar.edu.uade.route_planner.domain.Connection;
import ar.edu.uade.route_planner.domain.Station;
import ar.edu.uade.route_planner.repo.StationRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GraphService {
    private final StationRepo repo;

    public PathDto bfs(String from, String to, int maxDepth) {
        if (Objects.equals(from, to)) return new PathDto(List.of(from), 0, List.of(from));

        Queue<String> q = new ArrayDeque<>();
        Map<String,String> parent = new HashMap<>();
        Set<String> visited = new LinkedHashSet<>();

        q.add(from); visited.add(from);
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
                        return new PathDto(path, path.size() - 1, new ArrayList<>(visited));
                    }
                    q.add(v);
                }
            }
            depth++;
        }
        return new PathDto(List.of(), -1, new ArrayList<>(visited)); // no encontrado
    }

    private List<String> reconstruct(Map<String,String> parent, String from, String to) {
        LinkedList<String> path = new LinkedList<>();
        for (String cur = to; cur != null; cur = parent.get(cur)) path.addFirst(cur);
        return !path.isEmpty() && path.getFirst().equals(from) ? path : List.of();
    }

    // DTO simple para la respuesta
    public record PathDto(List<String> path, int hops, List<String> visited) {}
}
