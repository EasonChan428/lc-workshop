package workshop.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import workshop.FileWalker;
import workshop.Problem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Web 端的业务层：负责「加载并缓存题库」，具体的解析/检索/统计全部复用 workshop.FileWalker。
 * 这样 CLI 和 Web 用的是同一套逻辑，不会出现两边行为不一致。
 */
@Service
public class ProblemService {

    private final Path root;
    private volatile List<Problem> cache;

    @Autowired
    public ProblemService(@Value("${workshop.root:}") String configuredRoot) {
        this.root = resolveRoot(configuredRoot);
    }

    /** 测试用构造器：直接指定题库目录 */
    public ProblemService(Path root) {
        this.root = root;
    }

    /** 配置了就用配置；否则自动探测：当前目录下的 src/leetcode，再退化为当前目录 */
    static Path resolveRoot(String configuredRoot) {
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            return Path.of(configuredRoot.trim());
        }
        Path cwd = Path.of(".").toAbsolutePath().normalize();
        Path nested = cwd.resolve("src").resolve("leetcode");
        return Files.isDirectory(nested) ? nested : cwd;
    }

    public Path root() {
        return root;
    }

    /** 首次访问时加载，之后走缓存 */
    public List<Problem> all() {
        List<Problem> snapshot = cache;
        if (snapshot == null) {
            snapshot = reload();
        }
        return snapshot;
    }

    /** 重新扫描磁盘（页面上「刷新」按钮会调它） */
    public synchronized List<Problem> reload() {
        try {
            List<Problem> loaded = FileWalker.loadProblems(root);
            cache = loaded;
            return loaded;
        } catch (IOException e) {
            throw new UncheckedIOException("读取题库失败: " + root, e);
        }
    }

    public List<Problem> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return all();
        }
        return FileWalker.search(all(), keyword.trim());
    }

    public Optional<Problem> byNumber(int number) {
        return all().stream().filter(p -> p.number() == number).findFirst();
    }

    /** 相似题：优先共享标签；没有标签时退化为同主题 */
    public List<Problem> similar(int number) {
        return byNumber(number)
                .map(p -> {
                    List<Problem> byTags = FileWalker.similar(p, all());
                    return byTags.isEmpty() ? FileWalker.sameTopic(p, all()) : byTags;
                })
                .orElse(List.of());
    }

    /** 推荐依据：tags（共享标签）/ topic（同主题降级）/ none（什么都没有） */
    public String similarReason(int number) {
        return byNumber(number)
                .map(p -> {
                    if (!FileWalker.similar(p, all()).isEmpty()) {
                        return "tags";
                    }
                    return FileWalker.sameTopic(p, all()).isEmpty() ? "none" : "topic";
                })
                .orElse("none");
    }

    public Map<String, Long> countByTopic() {
        return FileWalker.countByTopic(all());
    }

    public Map<String, Long> tagFrequency() {
        return FileWalker.tagFrequency(all());
    }

    public int tagCoveragePercent() {
        return FileWalker.tagCoveragePercent(all());
    }

    public int untaggedCount() {
        return FileWalker.problemsWithoutTags(all()).size();
    }

    public List<Integer> untaggedNumbers() {
        return FileWalker.problemsWithoutTags(all()).stream().map(Problem::number).toList();
    }
}
