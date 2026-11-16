package workshop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class FileWalker {

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

    /** 从路径提取主题：...\leetcode\dp\P0279X.java -> "dp" */
    public static String extractTopic(Path file) {
        Path parent = file.getParent();
        return parent == null ? "" : parent.getFileName().toString();
    }

    /** 把一组文件解析成题目对象，过滤掉非题目文件，按题号升序 */
    public static List<Problem> parseProblems(List<Path> files) {
        return files.stream()
                .filter(p -> parseNumber(p.getFileName().toString()) > 0)
                .map(p -> new Problem(
                        parseNumber(p.getFileName().toString()),
                        extractTitle(p.getFileName().toString()),
                        extractTopic(p),
                        p.getFileName().toString()))
                .sorted(Comparator.comparingInt(Problem::number))
                .toList();
    }

    /** 按关键词检索：题号精确 / 主题精确 / 标题包含，均忽略大小写 */
    public static List<Problem> search(List<Problem> all, String keyword) {
        String k = keyword.toLowerCase();
        return all.stream()
                .filter(p -> String.valueOf(p.number()).equals(k)          // 题号：精确
                        || p.topic().equalsIgnoreCase(k)                    // 主题：精确
                        || p.title().toLowerCase().contains(k))             // 标题：包含
                .toList();
    }

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
}