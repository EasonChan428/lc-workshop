package workshop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /** 入口只负责「拿路径 -> 调方法 -> 打印结果」 */
    public static void main(String[] args) throws IOException {
        Path root = Path.of("C:\\Users\\eason\\Desktop\\MyCode\\git\\src\\leetcode");
        List<Path> files = findJavaFiles(root);
        for (Path p : files) {
            String name = p.getFileName().toString();
            System.out.println(parseNumber(name) + "  " + name);
        }
        System.out.println("total " + files.size() + " files");
    }
}