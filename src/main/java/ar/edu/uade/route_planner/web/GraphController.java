package ar.edu.uade.route_planner.web;

import ar.edu.uade.route_planner.service.GraphService;
import ar.edu.uade.route_planner.repo.StationRepo;
import ar.edu.uade.route_planner.repo.EdgeRecord;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/graph")
@RequiredArgsConstructor
@CrossOrigin
public class GraphController {
    private final GraphService svc;
    private final StationRepo repo;

    // Endpoint de diagnóstico
    @GetMapping("/debug/edges")
    public Map<String, Object> debugEdges() {
        List<EdgeRecord> edges = repo.allEdges();
        Map<String, Object> result = new HashMap<>();
        result.put("totalEdges", edges.size());
        result.put("edges", edges);
        return result;
    }

    @GetMapping("/bfs")
    public GraphService.PathDto bfs(@RequestParam String from,
                                    @RequestParam String to,
                                    @RequestParam(defaultValue = "6") int maxDepth) {
        return svc.bfs(from, to, maxDepth);
    }

    @GetMapping("/dfs")
    public GraphService.PathDto dfs(@RequestParam String from, 
                                    @RequestParam String to, 
                                    @RequestParam(defaultValue = "6") int depth) {
        return svc.dfs(from, to, depth);
    }

    @GetMapping("/dijkstra")
    public GraphService.DijkstraResult dijkstra(@RequestParam String from, 
                                                @RequestParam String to) {
        return svc.dijkstra(from, to);
    }

    @GetMapping("/prim")
    public GraphService.PrimResult primDirected(@RequestParam String from) {
        return svc.primDirected(from);
    }

    @GetMapping("/kruskal")
    public GraphService.KruskalResult kruskal() {
        return svc.kruskal();
    }
}