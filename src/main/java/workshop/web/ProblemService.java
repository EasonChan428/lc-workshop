package workshop.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import workshop.FileWalker;
import workshop.Problem;
import workshop.ai.AiClient;
import workshop.ai.AiConfig;
import workshop.ai.TagSuggester;
import workshop.ai.TagWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Web 端的业务层：负责「加载并缓存题库」，具体的解析/检索/统计全部复用 workshop.FileWalker。
 * 这样 CLI 和 Web 用的是同一套逻辑，不会出现两边行为不一致。
 */
@Service
public class ProblemService {

    /** 记住上次选择的题库目录（页面上选过一次，重启后仍然是它） */
    static final String LAST_ROOT_FILE = ".lc-workshop-root";

    /** 单题源码返回上限（字符数），避免异常大文件把页面卡死 */
    static final int MAX_CODE_CHARS = 200_000;

    private volatile Path root;
    private volatile List<Problem> cache;
    /** 文件名 -> 绝对路径，用于「查看源码」 */
    private volatile Map<String, Path> fileByName = Map.of();
    /** 「记住上次目录」写到哪个文件；null 表示不持久化（测试用，避免污染真实状态） */
    private final Path stateFile;

    /** 一道题的源码 */
    public record CodeFile(int number, String fileName, String code, boolean truncated) { }

    @Autowired
    public ProblemService(@Value("${workshop.root:}") String configuredRoot) {
        this(resolveRoot(configuredRoot), defaultStateFile());
    }

    /** 测试用构造器：指定题库目录且不写状态文件 */
    public ProblemService(Path root) {
        this(root, null);
    }

    /** 内部/测试用：可以指定状态文件位置，避免污染项目根目录 */
    ProblemService(Path root, Path stateFile) {
        this.root = root;
        this.stateFile = stateFile;
    }

    static Path defaultStateFile() {
        return Path.of(System.getProperty("workshop.state-file", LAST_ROOT_FILE));
    }

    /**
     * 优先级：配置/环境变量 > 上次页面选的目录 > 自动探测（当前目录/src/leetcode，否则当前目录）
     */
    static Path resolveRoot(String configuredRoot) {
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            return Path.of(configuredRoot.trim()).toAbsolutePath().normalize();
        }
        Path remembered = readRememberedRoot();
        if (remembered != null) {
            return remembered;
        }
        Path cwd = Path.of(".").toAbsolutePath().normalize();
        Path nested = cwd.resolve("src").resolve("leetcode");
        return Files.isDirectory(nested) ? nested : cwd;
    }

    static Path readRememberedRoot() {
        Path file = Path.of(LAST_ROOT_FILE);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                return null;
            }
            Path p = Path.of(text);
            return Files.isDirectory(p) ? p : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void remember(Path newRoot) {
        if (stateFile == null) {
            return;                     // 测试构造器：不写状态文件
        }
        try {
            Files.writeString(stateFile, newRoot.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("警告: 无法记住题库目录 -> " + e.getMessage());
        }
    }

    public Path root() {
        return root;
    }

    /** 切换题库目录：校验 -> 记住 -> 清缓存 -> 重新扫描 */
    public synchronized void switchRoot(String newRoot) throws IOException {
        if (newRoot == null || newRoot.isBlank()) {
            throw new IllegalArgumentException("目录不能为空");
        }
        Path target = Path.of(newRoot.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(target)) {
            throw new IllegalArgumentException("目录不存在: " + target);
        }
        this.root = target;
        this.cache = null;
        remember(target);
        reload();
    }

    /** 首次访问时加载，之后走缓存 */
    public List<Problem> all() {
        List<Problem> snapshot = cache;
        if (snapshot == null) {
            snapshot = reload();
        }
        return snapshot;
    }

    /** 重新扫描磁盘（页面上「重新扫描」按钮会调它） */
    public synchronized List<Problem> reload() {
        try {
            List<Path> files = FileWalker.findJavaFiles(root);
            Map<String, Path> byName = new HashMap<>();
            for (Path f : files) {
                byName.put(f.getFileName().toString(), f.toAbsolutePath().normalize());
            }
            List<Problem> loaded = FileWalker.loadProblems(root);
            this.fileByName = byName;
            this.cache = loaded;
            return loaded;
        } catch (IOException e) {
            throw new UncheckedIOException("读取题库失败: " + root, e);
        }
    }

    /**
     * 读取某道题的源码（只读，用于页面复习）。
     * 路径来自本次扫描结果，天然限定在题库目录内，不会因为请求参数被带到别处。
     */
    public Optional<CodeFile> codeOf(int number) {
        Optional<Problem> problem = byNumber(number);
        if (problem.isEmpty()) {
            return Optional.empty();
        }
        Problem p = problem.get();
        Path file = fileByName.get(p.fileName());
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            boolean truncated = text.length() > MAX_CODE_CHARS;
            if (truncated) {
                text = text.substring(0, MAX_CODE_CHARS);
            }
            return Optional.of(new CodeFile(p.number(), p.fileName(), text, truncated));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // ==================== AI：标签建议与写回 ====================

    /** 每次调用都重新读配置，改了 ai.properties 不用重启服务 */
    public AiConfig aiConfig() {
        return AiConfig.load();
    }

    public record TagSuggestion(int number, String title, List<String> currentTags,
                                List<String> suggestions) { }

    /**
     * 让模型给某道题建议标签（只建议，不写文件）。
     * 未配置密钥或调用失败时抛 AiException，由 Controller 转成合适的状态码。
     */
    public TagSuggestion suggestTags(int number) {
        Problem p = byNumber(number)
                .orElseThrow(() -> new IllegalArgumentException("没有这道题: " + number));
        CodeFile code = codeOf(number)
                .orElseThrow(() -> new IllegalArgumentException("读不到源码: " + number));

        TagSuggester suggester = new TagSuggester(new AiClient(aiConfig()));
        List<String> suggestions = suggester.suggest(p.number(), p.title(), p.topic(), code.code());
        return new TagSuggestion(p.number(), p.title(), p.tags(), suggestions);
    }

    /** 把人工确认过的标签写回源码文件（带 .bak 备份），随后重新扫描题库 */
    public TagWriter.ApplyResult applyTags(int number, List<String> tags) throws IOException {
        Problem p = byNumber(number)
                .orElseThrow(() -> new IllegalArgumentException("没有这道题: " + number));
        if (tags == null || tags.isEmpty()) {
            throw new IllegalArgumentException("标签列表不能为空");
        }
        Path file = fileByName.get(p.fileName());
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("找不到源码文件: " + p.fileName());
        }
        TagWriter.ApplyResult result = TagWriter.applyToFile(file, tags);
        if (result.changed()) {
            reload();
        }
        return result;
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
