package workshop.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 把 // @tag 行写进题解文件（纯文本变换，不依赖网络，方便测试）。
 *
 * 规则：
 *   - 标签块放在第一条类声明之前（import 之前也允许，Java 允许注释出现在 import 前）
 *   - 已有的 // @tag 行先全部移除，再插入新块 → 保证幂等
 *   - 保留原文件的换行符风格（CRLF 还是 LF）与末尾是否有换行
 */
public final class TagWriter {

    static final String TAG_PREFIX = "// @tag ";

    public record ApplyResult(boolean changed, Path file, Path backup) { }

    private TagWriter() {
    }

    /** 纯函数：给定文件内容和新标签，返回改写后的内容 */
    public static String withTags(String content, List<String> tags) {
        if (content == null || tags == null || tags.isEmpty()) {
            return content == null ? "" : content;
        }

        String newline = content.contains("\r\n") ? "\r\n" : "\n";
        String[] rawLines = content.split("\n", -1);

        List<String> lines = new ArrayList<>(rawLines.length);
        for (String raw : rawLines) {
            lines.add(stripCr(raw));
        }

        int classLine = findClassLine(lines);

        // 头部：类声明之前的行，去掉 @tag 行与尾部空行
        List<String> head = new ArrayList<>(lines.subList(0, classLine));
        head.removeIf(TagWriter::isTagLine);
        while (!head.isEmpty() && head.get(head.size() - 1).isBlank()) {
            head.remove(head.size() - 1);
        }

        // 正文：从类声明（或文件开头）到结尾，原样保留
        List<String> body = new ArrayList<>(lines.subList(classLine, lines.size()));

        List<String> result = new ArrayList<>(head.size() + tags.size() + body.size() + 1);
        result.addAll(head);
        for (String tag : tags) {
            result.add(TAG_PREFIX + tag);
        }
        result.add("");                 // 标签块与代码之间留一个空行
        result.addAll(body);

        // split(-1) 已经把「末尾是否有换行」体现为最后一个空串，join 后自然保持
        return String.join(newline, result);
    }

    /** 写回文件：先备份同目录 .bak，再写入（内容没变化则不动文件） */
    public static ApplyResult applyToFile(Path file, List<String> tags) throws IOException {
        String before = Files.readString(file, StandardCharsets.UTF_8);
        String after = withTags(before, tags);
        if (after.equals(before)) {
            return new ApplyResult(false, file, null);
        }
        Path backup = file.resolveSibling(file.getFileName() + ".bak");
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(file, after, StandardCharsets.UTF_8);
        return new ApplyResult(true, file, backup);
    }

    /** 找第一条类声明的行号；找不到返回 0（标签放最前面） */
    static int findClassLine(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("class ") || t.startsWith("public class ")
                    || t.startsWith("final class ") || t.startsWith("public final class ")) {
                return i;
            }
        }
        return 0;
    }

    static boolean isTagLine(String line) {
        String t = line.trim();
        if (!t.startsWith("//")) {
            return false;
        }
        return t.substring(2).trim().startsWith("@tag");
    }

    private static String stripCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
