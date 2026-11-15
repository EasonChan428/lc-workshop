package workshop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    /** 不传参数时，默认扫描你的刷题仓库 */
    static final Path DEFAULT_ROOT = Path.of("C:\\Users\\eason\\Desktop\\MyCode\\git\\src\\leetcode");

    /** 不传第二个参数时，默认写到当前目录的 index.md */
    static final Path DEFAULT_OUT = Path.of("index.md");

    /** 第一个参数 = 扫描目录；没传就用默认值 */
    public static Path resolveRoot(String[] args) {
        return args.length > 0 ? Path.of(args[0]) : DEFAULT_ROOT;
    }

    /** 第二个参数 = 输出文件；没传就用默认值 */
    public static Path resolveOut(String[] args) {
        return args.length > 1 ? Path.of(args[1]) : DEFAULT_OUT;
    }

    public static void main(String[] args) throws IOException {
        Path root = resolveRoot(args);
        Path out = resolveOut(args);

        // 参数校验：目录不存在就直接报错退出，不要往下走
        if (!Files.isDirectory(root)) {
            System.err.println("错误: 目录不存在或不是目录 -> " + root.toAbsolutePath());
            System.exit(1);
        }

        List<Path> files = FileWalker.findJavaFiles(root);
        String content = FileWalker.buildIndex(files);
        Files.writeString(out, content);

        System.out.println("扫描目录: " + root.toAbsolutePath());
        System.out.println("找到 .java 文件: " + files.size() + " 个");
        System.out.println("已写入: " + out.toAbsolutePath());
    }
}