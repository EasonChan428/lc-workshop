package workshop;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * 命令行入口。
 *
 * 用法：
 *   index   [题库目录] [README] [--write]   生成索引表并写回 README（默认只预览）
 *   find    <关键词> [题库目录]             按题号/标题/主题/标签检索
 *   similar <题号或关键词> [题库目录]       推荐共享标签的相似题（无标签时退化为同主题）
 *   stats   [题库目录]                      统计主题分布、标签覆盖率、未打标签清单
 *
 * 默认值来源优先级：命令行参数 > lc-workshop.properties > 自动探测当前目录
 */
public class Main {

    static final String CONFIG_FILE = "lc-workshop.properties";

    /** 已知的子命令 */
    static final Set<String> COMMANDS = Set.of("index", "find", "similar", "stats");

    /** 默认值（可以从配置文件读、也可以从当前目录猜，测试时可注入） */
    record Defaults(Path root, Path readme) {
        static Defaults fallback() {
            return new Defaults(Path.of("."), Path.of("README.md"));
        }
    }

    record Options(String command, Path root, Path readme, String keyword, boolean write) { }

    // ==================== 参数解析 ====================

    public static Options parseArgs(String[] args) {
        return parseArgs(args, Defaults.fallback());
    }

    public static Options parseArgs(String[] args, Defaults defaults) {
        // 1) 分离标志和位置参数
        boolean write = false;
        List<String> positional = new ArrayList<>();
        for (String a : args) {
            if (a.startsWith("--")) {
                if (a.equals("--write")) {
                    write = true;
                }
            } else {
                positional.add(a);
            }
        }

        // 2) 第一个位置参数若是已知命令，就当子命令；否则默认 index
        String command = "index";
        int from = 0;
        if (!positional.isEmpty() && COMMANDS.contains(positional.get(0))) {
            command = positional.get(0);
            from = 1;
        }
        List<String> rest = positional.subList(from, positional.size());

        // 3) 按子命令解释剩下的位置参数
        switch (command) {
            case "find":
            case "similar": {
                String keyword = rest.size() > 0 ? rest.get(0) : "";
                Path root = rest.size() > 1 ? Path.of(rest.get(1)) : defaults.root();
                return new Options(command, root, defaults.readme(), keyword, write);
            }
            case "stats": {
                Path root = rest.size() > 0 ? Path.of(rest.get(0)) : defaults.root();
                return new Options(command, root, defaults.readme(), "", write);
            }
            default: {
                Path root = rest.size() > 0 ? Path.of(rest.get(0)) : defaults.root();
                Path readme = rest.size() > 1 ? Path.of(rest.get(1)) : defaults.readme();
                return new Options(command, root, readme, "", write);
            }
        }
    }

    // ==================== 默认值：配置 + 自动探测 ====================

    static Defaults loadDefaults() {
        Properties props = loadConfig();
        String cfgRoot = props.getProperty("root", "").trim();
        String cfgReadme = props.getProperty("readme", "").trim();

        Path root = cfgRoot.isEmpty() ? detectRoot() : Path.of(cfgRoot);
        Path readme = cfgReadme.isEmpty() ? Path.of("README.md") : Path.of(cfgReadme);
        return new Defaults(root, readme);
    }

    /** 读配置文件：优先当前目录，其次用户主目录 */
    static Properties loadConfig() {
        Properties props = new Properties();
        List<Path> candidates = List.of(
                Path.of(CONFIG_FILE),
                Path.of(System.getProperty("user.home"), CONFIG_FILE));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                try (InputStream in = Files.newInputStream(candidate)) {
                    props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    System.err.println("警告: 读取配置失败 " + candidate.toAbsolutePath() + " -> " + e.getMessage());
                }
                break;
            }
        }
        return props;
    }

    /** 猜一个合理的默认目录：当前目录 / 当前目录下的 src/leetcode */
    static Path detectRoot() {
        Path cwd = Path.of(".").toAbsolutePath().normalize();
        Path nested = cwd.resolve("src").resolve("leetcode");
        if (Files.isDirectory(nested)) {
            return nested;
        }
        return cwd;
    }

    // ==================== 入口 ====================

    public static void main(String[] args) throws IOException {
        Options options = parseArgs(args, loadDefaults());
        switch (options.command()) {
            case "find" -> runFind(options);
            case "similar" -> runSimilar(options);
            case "stats" -> runStats(options);
            default -> runIndex(options);
        }
    }

    // ==================== index ====================

    /** index：生成表格并写回 README（默认只预览） */
    static void runIndex(Options options) throws IOException {
        Path root = options.root();
        Path readme = options.readme();
        requireDirectory(root);
        if (!Files.isRegularFile(readme)) {
            System.err.println("错误: README 文件不存在 -> " + readme.toAbsolutePath());
            System.exit(1);
        }

        List<Path> files = FileWalker.findJavaFiles(root);
        long problemCount = FileWalker.parseProblems(files).size();
        if (problemCount == 0) {
            System.err.println("错误: 在 " + root.toAbsolutePath() + " 下没找到任何题目文件，拒绝写入");
            System.exit(2);
        }

        String table = FileWalker.buildIndex(files);
        String original = Files.readString(readme);
        String updated = IndexInjector.replaceSection(original, table);
        boolean changed = !updated.equals(original);

        System.out.println("扫描目录  : " + root.toAbsolutePath());
        System.out.println("README    : " + readme.toAbsolutePath());
        System.out.println("题目数量  : " + problemCount);
        System.out.println("内容有变化: " + changed);

        if (!options.write()) {
            System.out.println();
            System.out.println("【预览模式】未修改任何文件。确认无误后加 --write 才会真正写入。");
            return;
        }
        if (!changed) {
            System.out.println();
            System.out.println("内容无变化，跳过写入。");
            return;
        }

        Path backup = readme.resolveSibling(readme.getFileName() + ".bak");
        Files.copy(readme, backup, StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(readme, updated);

        System.out.println();
        System.out.println("已备份: " + backup.toAbsolutePath());
        System.out.println("已写入: " + readme.toAbsolutePath());
    }

    // ==================== find ====================

    /** find：按关键词检索题目 */
    static void runFind(Options options) throws IOException {
        Path root = options.root();
        requireDirectory(root);

        String keyword = options.keyword();
        if (keyword.isBlank()) {
            System.err.println("用法: find <关键词> [题库目录]");
            System.exit(2);
        }

        List<Problem> all = loadProblems(root);
        List<Problem> hits = FileWalker.search(all, keyword);

        if (hits.isEmpty()) {
            System.out.println("没有匹配「" + keyword + "」的题目（共扫描 " + all.size() + " 题）");
            return;
        }
        System.out.println("匹配「" + keyword + "」共 " + hits.size() + " / " + all.size() + " 题：");
        for (Problem p : hits) {
            System.out.printf("%5d  %-12s %-38s %-24s %s%n",
                    p.number(), p.topic(), p.title(), tagsText(p), p.fileName());
        }
    }

    // ==================== similar ====================

    /** similar：推荐相似题；该题没标签时退化为同主题推荐 */
    static void runSimilar(Options options) throws IOException {
        Path root = options.root();
        requireDirectory(root);

        String keyword = options.keyword();
        if (keyword.isBlank()) {
            System.err.println("用法: similar <题号或关键词> [题库目录]");
            System.exit(2);
        }

        List<Problem> all = loadProblems(root);
        List<Problem> matched = FileWalker.search(all, keyword);
        if (matched.isEmpty()) {
            System.out.println("没有找到「" + keyword + "」对应的题目");
            return;
        }

        Problem target = matched.get(0);
        System.out.println("基准题目: " + target.number() + "  " + target.title()
                + "   主题: " + target.topic() + "   标签: " + tagsText(target));

        List<Problem> byTags = FileWalker.similar(target, all);
        if (!byTags.isEmpty()) {
            System.out.println("共享标签的相似题 " + byTags.size() + " 道：");
            for (Problem p : byTags) {
                System.out.printf("%5d  %-12s %-36s 共享 %d 个: %s%n",
                        p.number(), p.topic(), p.title(), p.sharedTagCount(target), tagsText(p));
            }
            return;
        }

        List<Problem> sameTopic = FileWalker.sameTopic(target, all);
        if (sameTopic.isEmpty()) {
            System.out.println("没有找到相似题（该题既没有标签，同主题下也没有其它题）");
            return;
        }
        System.out.println("（该题还没有标签，退化为同主题推荐）主题「" + target.topic()
                + "」共 " + sameTopic.size() + " 道：");
        for (Problem p : sameTopic) {
            System.out.printf("%5d  %-12s %-36s %s%n", p.number(), p.topic(), p.title(), tagsText(p));
        }
    }

    // ==================== stats ====================

    /** stats：主题分布 + 标签覆盖率 + 未打标签清单 */
    static void runStats(Options options) throws IOException {
        Path root = options.root();
        requireDirectory(root);

        List<Problem> all = loadProblems(root);
        if (all.isEmpty()) {
            System.out.println("在 " + root.toAbsolutePath() + " 下没有找到题目");
            return;
        }

        System.out.println("扫描目录  : " + root.toAbsolutePath());
        System.out.println("题目总数  : " + all.size());
        System.out.println("标签覆盖率: " + FileWalker.tagCoveragePercent(all) + "%（"
                + (all.size() - FileWalker.problemsWithoutTags(all).size()) + " / " + all.size() + " 道题有标签）");

        System.out.println();
        System.out.println("按主题分布：");
        FileWalker.countByTopic(all).forEach((topic, count) ->
                System.out.printf("  %-16s %3d%n", topic, count));

        Map<String, Long> freq = FileWalker.tagFrequency(all);
        System.out.println();
        if (freq.isEmpty()) {
            System.out.println("标签频次     : （还没有任何标签）");
        } else {
            System.out.println("标签频次：");
            freq.forEach((tag, count) -> System.out.printf("  %-16s %3d%n", tag, count));
        }

        List<Problem> untagged = FileWalker.problemsWithoutTags(all);
        System.out.println();
        if (untagged.isEmpty()) {
            System.out.println("所有题目都已打标签 🎉");
            return;
        }
        System.out.println("未打标签 " + untagged.size() + " 道（最多显示 20 个题号）：");
        StringBuilder sb = new StringBuilder("  ");
        for (int i = 0; i < Math.min(20, untagged.size()); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(untagged.get(i).number());
        }
        if (untagged.size() > 20) {
            sb.append(" ...");
        }
        System.out.println(sb);
    }

    // ==================== 公共 ====================

    /** 扫描 + 解析 + 贴标签（委托给 FileWalker，CLI 与 Web 共用同一套逻辑） */
    static List<Problem> loadProblems(Path root) throws IOException {
        return FileWalker.loadProblems(root);
    }

    /** 统一校验：扫描目录必须存在 */
    static void requireDirectory(Path root) {
        if (!Files.isDirectory(root)) {
            System.err.println("错误: 扫描目录不存在 -> " + root.toAbsolutePath());
            System.exit(1);
        }
    }

    private static String tagsText(Problem p) {
        return p.tags().isEmpty() ? "-" : String.join(",", p.tags());
    }
}
