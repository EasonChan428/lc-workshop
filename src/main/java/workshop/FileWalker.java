package workshop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 「题解工具」的纯逻辑集合：文件名解析、索引生成、标签解析、检索、相似题、统计。
 * 除 findJavaFiles / extractTags 需要读磁盘外，其余方法都是纯函数（同样输入永远同样输出）。
 */
public class FileWalker {

    // ==================== 文件名解析 ====================

    /**
     * 从文件名解析题号：P0279PerfectSquares.java -> 279
     * 解析不出来时返回 -1（约定：负数表示无效）
     */
    public static int parseNumber(String fileName) {
        if (fileName == null || fileName.length() < 5 || fileName.charAt(0) != 'P') {
            return -1;
        }
        String digits = fileName.substring(1, 5);
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return -1;
            }
        }
        return Integer.parseInt(digits);
    }

    /**
     * 从文件名提取英文标题：P0279PerfectSquares.java -> "Perfect Squares"
     * 做法：先切掉前缀 "P0279" 和后缀 ".java"，再在大写字母前插空格
     */
    public static String extractTitle(String fileName) {
        if (parseNumber(fileName) < 0) {
            return "";
        }
        String camel = fileName.substring(5, fileName.length() - ".java".length());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            // 只有「大写且前一个是小写」才断词：JumpGameII -> Jump Game II
            if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(camel.charAt(i - 1))) {
                sb.append(' ');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** 从路径提取主题：...\leetcode\dp\P0279X.java -> "dp" */
    public static String extractTopic(Path file) {
        Path parent = file.getParent();
        return parent == null ? "" : parent.getFileName().toString();
    }

    // ==================== 标签 ====================

    /**
     * 从文件内容解析 // @tag 行（纯函数，好测）。
     * 读到类声明就停：类后面可能还有被注释掉的示例代码，不能误读。
     */
    public static List<String> parseTags(List<String> lines) {
        List<String> tags = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("class ") || t.startsWith("public class ") || t.startsWith("final class ")) {
                break;
            }
            if (!t.startsWith("//")) {
                continue;
            }
            String content = t.substring(2).trim();
            if (content.startsWith("@tag")) {
                String tag = content.substring("@tag".length()).trim();
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
        }
        return tags;
    }

    /** 读文件并解析标签（IO） */
    public static List<String> extractTags(Path file) throws IOException {
        return parseTags(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    /** 把「文件名 -> 标签」应用到题目列表上（纯函数） */
    public static List<Problem> applyTags(List<Problem> problems, Map<String, List<String>> tagsByFileName) {
        return problems.stream()
                .map(p -> new Problem(p.number(), p.title(), p.topic(), p.fileName(),
                        tagsByFileName.getOrDefault(p.fileName(), List.of())))
                .toList();
    }

    // ==================== 题目对象 ====================

    /** 把一组文件解析成题目对象，过滤掉非题目文件，按题号升序（标签默认为空） */
    public static List<Problem> parseProblems(List<Path> files) {
        return files.stream()
                .filter(p -> parseNumber(p.getFileName().toString()) > 0)
                .map(p -> new Problem(
                        parseNumber(p.getFileName().toString()),
                        extractTitle(p.getFileName().toString()),
                        extractTopic(p),
                        p.getFileName().toString(),
                        List.of()))
                .sorted(Comparator.comparingInt(Problem::number))
                .toList();
    }

    // ==================== 检索与推荐 ====================

    /** 按关键词检索：题号精确 / 主题精确 / 标题包含 / 标签包含，均忽略大小写 */
    public static List<Problem> search(List<Problem> all, String keyword) {
        String k = keyword.toLowerCase();
        return all.stream()
                .filter(p -> String.valueOf(p.number()).equals(k)                       // 题号：精确
                        || p.topic().equalsIgnoreCase(k)                                 // 主题：精确
                        || p.title().toLowerCase().contains(k)                           // 标题：包含
                        || p.tags().stream().anyMatch(tag -> tag.toLowerCase().contains(k))) // 标签：包含
                .toList();
    }

    /** 共享至少一个标签的题目，按共享标签数降序、题号升序 */
    public static List<Problem> similar(Problem target, List<Problem> all) {
        return all.stream()
                .filter(p -> p.number() != target.number())
                .filter(p -> p.sharedTagCount(target) > 0)
                .sorted(Comparator.comparingInt((Problem p) -> p.sharedTagCount(target)).reversed()
                        .thenComparingInt(Problem::number))
                .toList();
    }

    /** 同一主题下的其它题目（相似题的降级方案：没打标签时用） */
    public static List<Problem> sameTopic(Problem target, List<Problem> all) {
        return all.stream()
                .filter(p -> p.number() != target.number())
                .filter(p -> p.topic().equals(target.topic()))
                .sorted(Comparator.comparingInt(Problem::number))
                .toList();
    }

    // ==================== 统计 ====================

    /** 按主题统计题数，数量降序、主题名升序 */
    public static Map<String, Long> countByTopic(List<Problem> problems) {
        return sortedByCountDesc(problems.stream()
                .collect(Collectors.groupingBy(Problem::topic, Collectors.counting())));
    }

    /** 标签出现频次，频次降序、标签名升序 */
    public static Map<String, Long> tagFrequency(List<Problem> problems) {
        return sortedByCountDesc(problems.stream()
                .flatMap(p -> p.tags().stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting())));
    }

    /** 还没打标签的题目（按题号升序） */
    public static List<Problem> problemsWithoutTags(List<Problem> problems) {
        return problems.stream()
                .filter(p -> !p.hasTags())
                .sorted(Comparator.comparingInt(Problem::number))
                .toList();
    }

    /** 标签覆盖率（百分比，四舍五入） */
    public static int tagCoveragePercent(List<Problem> problems) {
        if (problems.isEmpty()) {
            return 0;
        }
        long tagged = problems.stream().filter(Problem::hasTags).count();
        return (int) Math.round(tagged * 100.0 / problems.size());
    }

    private static Map<String, Long> sortedByCountDesc(Map<String, Long> raw) {
        return raw.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    // ==================== 索引生成 ====================

    /** 生成一行 Markdown 表格，例如：| 279 | Perfect Squares | P0279PerfectSquares.java | */
    public static String buildRow(Path file) {
        String name = file.getFileName().toString();
        return "| " + parseNumber(name) + " | " + extractTitle(name) + " | " + name + " |";
    }

    /** 生成整张索引表（纯函数：不写文件、不打印） */
    public static String buildIndex(List<Path> files) {
        List<Path> sorted = files.stream()
                .filter(p -> parseNumber(p.getFileName().toString()) > 0)
                .sorted(Comparator.comparingInt((Path p) -> parseNumber(p.getFileName().toString())))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("| 题号 | 标题 | 文件 |\n");
        sb.append("| --- | --- | --- |\n");
        for (Path p : sorted) {
            sb.append(buildRow(p)).append("\n");
        }
        sb.append("\n共 ").append(sorted.size()).append(" 题\n");
        return sb.toString();
    }

    // ==================== 磁盘扫描 ====================

    /** 找出 root 目录下所有 .java 文件，按路径排序返回 */
    public static List<Path> findJavaFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        }
    }

    /** 一步到位：扫描目录 -> 解析题目 -> 贴上标签（需要读磁盘） */
    public static List<Problem> loadProblems(Path root) throws IOException {
        List<Path> files = findJavaFiles(root);
        List<Problem> base = parseProblems(files);
        Map<String, List<String>> tagsByFile = new HashMap<>();
        for (Path f : files) {
            tagsByFile.put(f.getFileName().toString(), extractTags(f));
        }
        return applyTags(base, tagsByFile);
    }
}
