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
            // 首字母前不插空格（i > 0），其余每个大写字母前插一个空格
            if (i > 0 && Character.isUpperCase(c)) {
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