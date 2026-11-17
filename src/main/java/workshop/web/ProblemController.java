package workshop.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import workshop.Problem;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * REST 接口。所有方法都只是「把 service 的结果包成 JSON」，没有业务逻辑。
 *
 * GET  /api/problems?q=关键词      → 题目列表（q 为空则返回全部）
 * GET  /api/problems/{number}      → 单道题
 * GET  /api/problems/{number}/similar → 相似题推荐
 * GET  /api/stats                  → 统计
 * POST /api/reload                 → 重新扫描磁盘
 */
@RestController
@RequestMapping("/api")
public class ProblemController {

    private final ProblemService service;

    public ProblemController(ProblemService service) {
        this.service = service;
    }

    public record ProblemsResponse(String root, int total, String keyword, List<Problem> problems) { }

    public record SimilarItem(Problem problem, int sharedTags) { }

    public record SimilarResponse(Problem target, String reason, List<SimilarItem> items) { }

    public record StatsResponse(int total, int tagged, int untagged, int coveragePercent,
                                Map<String, Long> byTopic, Map<String, Long> tagFrequency,
                                List<Integer> untaggedNumbers) { }

    @GetMapping("/problems")
    public ProblemsResponse problems(@RequestParam(name = "q", defaultValue = "") String q) {
        List<Problem> list = service.search(q);
        return new ProblemsResponse(service.root().toString(), service.all().size(), q, list);
    }

    @GetMapping("/problems/{number}")
    public ResponseEntity<Problem> one(@PathVariable("number") int number) {
        return service.byNumber(number)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/problems/{number}/similar")
    public ResponseEntity<SimilarResponse> similar(@PathVariable("number") int number) {
        return service.byNumber(number)
                .map(target -> {
                    List<SimilarItem> items = service.similar(number).stream()
                            .map(p -> new SimilarItem(p, p.sharedTagCount(target)))
                            .toList();
                    return ResponseEntity.ok(new SimilarResponse(target, service.similarReason(number), items));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 某道题的源码（只读，用于页面复习） */
    @GetMapping("/problems/{number}/code")
    public ResponseEntity<ProblemService.CodeFile> code(@PathVariable("number") int number) {
        return service.codeOf(number)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        List<Problem> all = service.all();
        int total = all.size();
        int untagged = service.untaggedCount();
        return new StatsResponse(total, total - untagged, untagged, service.tagCoveragePercent(),
                service.countByTopic(), service.tagFrequency(), service.untaggedNumbers());
    }

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        service.reload();
        return Map.of(
                "ok", true,
                "root", service.root().toString(),
                "total", service.all().size());
    }

    /** 浏览目录：GET /api/dirs?path=D:/xxx（path 为空则从常见入口开始） */
    @GetMapping("/dirs")
    public DirBrowser.Listing dirs(@RequestParam(name = "path", defaultValue = "") String path) throws IOException {
        if (path == null || path.isBlank()) {
            Path cwd = service.root();
            return DirBrowser.list(cwd);
        }
        return DirBrowser.list(Path.of(path.trim()));
    }

    /** 常见入口：盘符、用户目录、当前目录 */
    @GetMapping("/roots")
    public List<DirBrowser.Entry> roots() {
        return DirBrowser.suggestions();
    }

    /** 切换题库目录：POST /api/root {"path": "D:/my-leetcode/src/leetcode"} */
    @PostMapping("/root")
    public ResponseEntity<?> switchRoot(@RequestBody Map<String, String> body) {
        String path = body == null ? null : body.get("path");
        try {
            service.switchRoot(path);
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "root", service.root().toString(),
                    "total", service.all().size()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("ok", false, "error", e.getMessage()));
        }
    }
}
