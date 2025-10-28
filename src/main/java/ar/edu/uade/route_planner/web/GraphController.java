package ar.edu.uade.route_planner.web;

import ar.edu.uade.route_planner.service.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/graph")
@RequiredArgsConstructor
@CrossOrigin
public class GraphController {
    private final GraphService svc;

    @GetMapping("/bfs")
    public GraphService.PathDto bfs(@RequestParam String from,
                                    @RequestParam String to,
                                    @RequestParam(defaultValue = "6") int maxDepth) {
        return svc.bfs(from, to, maxDepth);
    }
}
