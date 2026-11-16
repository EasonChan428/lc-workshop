package workshop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Main {

    static final Path DEFAULT_ROOT = Path.of("C:\\Users\\eason\\Desktop\\MyCode\\git\\src\\leetcode");
    static final Path DEFAULT_README = Path.of("README.md");

    /** 已知的子命令 */
    static final Set<String> COMMANDS = Set.of("index", "find");

    record Options(String command, Path root, Path readme, String keyword, boolean write) { }

    public static Options parseArgs(String[] args) {
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

        // 2) 第一个位置参数若是已知命令，就当子命令
        String command = "index";
        int from = 0;
        if (!positional.isEmpty() && COMMANDS.contains(positional.get(0))) {
            command = positional.get(0);
            from = 1;
        }
        List<String> rest = positional.subList(from, positional.size());

        // 3) 按子命令解释剩下的位置参数
        if (command.equals("find")) {
            String keyword = rest.size() > 0 ? rest.get(0) : "";
            Path root = rest.size() > 1 ? Path.of(rest.get(1)) : DEFAULT_ROOT;
            return new Options(command, root, DEFAULT_README, keyword, write);
        }
        Path root = rest.size() > 0 ? Path.of(rest.get(0)) : DEFAULT_ROOT;
        Path readme = rest.size() > 1 ? Path.of(rest.get(1)) : DEFAULT_README;
        return new Options(command, root, readme, "", write);
    }

    public static void main(String[] args) throws IOException {
        Options options = parseArgs(args);
        switch (options.command()) {
            case "find" -> runFind(options);
            default -> runIndex(options);
        }
    }

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

    /** find：按关键词检索题目 */
    static void runFind(Options options) throws IOException {
        Path root = options.root();
        requireDirectory(root);

        String keyword = options.keyword();
        if (keyword.isBlank()) {
            System.err.println("用法: find <关键词> [题库目录]");
            System.exit(2);
        }

        List<Problem> all = FileWalker.parseProblems(FileWalker.findJavaFiles(root));
        List<Problem> hits = FileWalker.search(all, keyword);

        if (hits.isEmpty()) {
            System.out.println("没有匹配「" + keyword + "」的题目（共扫描 " + all.size() + " 题）");
            return;
        }
        System.out.println("匹配「" + keyword + "」共 " + hits.size() + " / " + all.size() + " 题：");
        for (Problem p : hits) {
            System.out.printf("%5d  %-14s %-42s %s%n", p.number(), p.topic(), p.title(), p.fileName());
        }
    }

    /** 统一校验：扫描目录必须存在 */
    static void requireDirectory(Path root) {
        if (!Files.isDirectory(root)) {
            System.err.println("错误: 扫描目录不存在 -> " + root.toAbsolutePath());
            System.exit(1);
        }
    }
}