package ar.edu.uade.route_planner.web;

import ar.edu.uade.route_planner.service.GraphService;
import ar.edu.uade.route_planner.repo.StationRepo;
import ar.edu.uade.route_planner.repo.StationRepo.SimpleEdge;
import ar.edu.uade.route_planner.repo.EdgeRecord;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;



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
        List<SimpleEdge> edges = repo.allEdgesSimple();
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
    public GraphService.PathDto getMethodName(@RequestParam String from, @RequestParam String to) {
        return svc.dijkstra(from, to);
    }

    @GetMapping("/prim")
    public GraphService.MSTDto prim(@RequestParam String start) {
        return svc.prim(start);
    }
    
    
    @GetMapping("/kruskal")
    public GraphService.MSTDto kruskal() {
        return svc.kruskal();
    }

    

   

}