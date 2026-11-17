package workshop.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 服务端目录浏览：浏览器的 JavaScript 拿不到真实文件路径，所以由后端列目录、前端点选。
 * 纯逻辑 + 只读 IO，容易测。
 */
public final class DirBrowser {

    /** 一次最多列多少个子目录，避免选中巨大目录时卡死 */
    static final int MAX_ENTRIES = 500;

    public record Entry(String name, String path, boolean looksLikeLeetCode) { }

    public record Listing(String path, String parent, boolean looksLikeLeetCode, List<Entry> dirs) { }

    private DirBrowser() {
    }

    public static Listing list(Path dir) throws IOException {
        Path abs = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(abs)) {
            throw new IllegalArgumentException("不是目录: " + abs);
        }

        List<Entry> dirs = new ArrayList<>();
        try (Stream<Path> stream = Files.list(abs)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .limit(MAX_ENTRIES)
                    .forEach(p -> dirs.add(new Entry(
                            p.getFileName().toString(),
                            p.toString(),
                            looksLikeLeetCode(p))));
        } catch (IOException e) {
            // 权限不足的目录：返回空列表而不是整个请求失败
            return new Listing(abs.toString(), parentOf(abs), looksLikeLeetCode(abs), List.of());
        }

        return new Listing(abs.toString(), parentOf(abs), looksLikeLeetCode(abs), dirs);
    }

    /** 猜这个目录是不是「题解仓库」：有 src/leetcode 目录，或直接放着 P0001Xxx.java 这类文件 */
    public static boolean looksLikeLeetCode(Path dir) {
        if (Files.isDirectory(dir.resolve("src").resolve("leetcode"))) {
            return true;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.anyMatch(p -> p.getFileName().toString().matches("P\\d{4}.*\\.java"));
        } catch (IOException e) {
            return false;
        }
    }

    /** 常见入口：所有盘符 + 用户主目录 + 当前目录 */
    public static List<Entry> suggestions() {
        List<Entry> list = new ArrayList<>();
        for (java.io.File root : java.io.File.listRoots()) {
            Path p = root.toPath().toAbsolutePath();
            list.add(new Entry(p.toString(), p.toString(), DirBrowser.looksLikeLeetCode(p)));
        }
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath();
        list.add(new Entry("用户目录", home.toString(), looksLikeLeetCode(home)));
        Path cwd = Path.of(".").toAbsolutePath().normalize();
        list.add(new Entry("当前目录", cwd.toString(), looksLikeLeetCode(cwd)));
        Path nested = cwd.resolve("src").resolve("leetcode");
        if (Files.isDirectory(nested)) {
            list.add(new Entry("当前目录/src/leetcode", nested.toString(), true));
        }
        return list;
    }

    private static String parentOf(Path abs) {
        Path parent = abs.getParent();
        return parent == null ? null : parent.toString();
    }
}
