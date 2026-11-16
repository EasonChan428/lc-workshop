package workshop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class Main {

    /** 不传第一个位置参数时，默认扫描你的刷题仓库 */
    static final Path DEFAULT_ROOT = Path.of("C:\\Users\\eason\\Desktop\\MyCode\\git\\src\\leetcode");

    /** 不传第二个位置参数时，默认更新当前目录的 README.md */
    static final Path DEFAULT_README = Path.of("README.md");

    /** 解析后的命令行参数（record 是 Java 16+ 的简写类：自动生成构造器和取值方法） */
    record Options(Path root, Path readme, boolean write) { }

    /**
     * 把 args 解析成 Options：
     *   -- 开头的是标志（目前只有 --write）
     *   其余按出现顺序作为位置参数：第 1 个=扫描目录，第 2 个=README 路径
     */
    public static Options parseArgs(String[] args) {
        boolean write = false;
        List<String> positional = new ArrayList<>();
        for (String a : args) {
            if (a.startsWith("--")) {
                if (a.equals("--write")) {
                    write = true;
                }
                // 其他未知标志先忽略
            } else {
                positional.add(a);
            }
        }
        Path root = positional.size() > 0 ? Path.of(positional.get(0)) : DEFAULT_ROOT;
        Path readme = positional.size() > 1 ? Path.of(positional.get(1)) : DEFAULT_README;
        return new Options(root, readme, write);
    }

    public static void main(String[] args) throws IOException {
        Options options = parseArgs(args);
        Path root = options.root();
        Path readme = options.readme();

        if (!Files.isDirectory(root)) {
            System.err.println("错误: 扫描目录不存在 -> " + root.toAbsolutePath());
            System.exit(1);
        }
        if (!Files.isRegularFile(readme)) {
            System.err.println("错误: README 文件不存在 -> " + readme.toAbsolutePath());
            System.exit(1);
        }

        // 生成新表格
        List<Path> files = FileWalker.findJavaFiles(root);
        long problemCount = files.stream()
                .filter(p -> FileWalker.parseNumber(p.getFileName().toString()) > 0)
                .count();
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
}