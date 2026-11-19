package workshop.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 相关逻辑的离线测试：不联网，只测「提示词构造 + 输出解析 + 文件写回」这些确定性的部分。
 * 真正调用模型的行为只能靠联调，但把可测的部分测干净，问题就会被压缩到很小一块。
 */
class AiTagTest {

    // ==================== 输出解析（LLM 的输出不可靠，重点测这里） ====================

    @Test
    void 应该解析纯JSON数组() {
        assertEquals(List.of("完全背包", "最值型"),
                TagSuggester.parseTags("[\"完全背包\",\"最值型\"]"));
    }

    @Test
    void 应该剥掉markdown代码围栏() {
        String raw = """
                ```json
                ["区间DP", "回文"]
                ```
                """;
        assertEquals(List.of("区间DP", "回文"), TagSuggester.parseTags(raw));
    }

    @Test
    void 应该容忍模型多说几句话() {
        String raw = "好的，这道题的标签是：[\"完全背包\",\"可行性\"] 希望能帮到你。";
        assertEquals(List.of("完全背包", "可行性"), TagSuggester.parseTags(raw));
    }

    @Test
    void 应该兼容列表式输出() {
        String raw = """
                1. 完全背包
                2. 最值型
                """;
        assertEquals(List.of("完全背包", "最值型"), TagSuggester.parseTags(raw));
    }

    @Test
    void 应该去掉项目符号引号并去重与限量() {
        List<String> tags = TagSuggester.parseTags("[\"- 双状态\", \"'滑动窗口'\", \"双状态\", \"单调栈\", \"第四个别要\"]");
        assertEquals(3, tags.size());
        assertEquals("双状态", tags.get(0));
        assertEquals("滑动窗口", tags.get(1));
        assertEquals("单调栈", tags.get(2));
    }

    @Test
    void 空输出或非法输出应该返回空列表() {
        assertEquals(List.of(), TagSuggester.parseTags(null));
        assertEquals(List.of(), TagSuggester.parseTags("   "));
        assertEquals(List.of(), TagSuggester.parseTags("我不知道"));
    }

    @Test
    void 超长标签应该被丢弃() {
        assertEquals(List.of("01背包"), TagSuggester.parseTags("[\"01背包\", \"这是一个特别特别长的标签名称不应该出现\"]"));
    }

    @Test
    void 用户提示里超长代码应该被截断() {
        String longCode = "x".repeat(TagSuggester.MAX_CODE_CHARS + 500);
        String prompt = TagSuggester.buildUserPrompt(322, "Coin Change", "dp", longCode);
        assertTrue(prompt.contains("代码过长已截断"));
        assertTrue(prompt.length() < TagSuggester.MAX_CODE_CHARS + 500);
        assertTrue(prompt.contains("题号：322"));
    }

    // ==================== 写回文件 ====================

    @Test
    void 应该在类声明前插入标签并保留原有内容() {
        String original = "import java.util.*;\n\nclass P0322CoinChange {\n    int f() { return 1; }\n}\n";
        String updated = TagWriter.withTags(original, List.of("完全背包", "最值型"));

        assertTrue(updated.contains("// @tag 完全背包\n// @tag 最值型\n"));
        assertTrue(updated.contains("import java.util.*;"));
        assertTrue(updated.contains("class P0322CoinChange {"));
        // 标签必须在类声明之前
        assertTrue(updated.indexOf("// @tag") < updated.indexOf("class P0322CoinChange"));
    }

    @Test
    void 重复写入应该幂等() {
        String original = "class A {\n}\n";
        String once = TagWriter.withTags(original, List.of("双指针"));
        String twice = TagWriter.withTags(once, List.of("双指针"));
        assertEquals(once, twice);
    }

    @Test
    void 应该替换已有标签而不是叠加() {
        String original = "// @tag 旧标签\n// @tag 另一个旧标签\nclass A {\n}\n";
        String updated = TagWriter.withTags(original, List.of("新标签"));

        assertTrue(updated.contains("// @tag 新标签"));
        assertFalse(updated.contains("旧标签"));
        assertEquals(1, updated.lines().filter(l -> l.contains("@tag")).count());
    }

    @Test
    void 应该保留CRLF换行风格() {
        String original = "import java.util.*;\r\n\r\nclass A {\r\n}\r\n";
        String updated = TagWriter.withTags(original, List.of("区间DP"));
        assertTrue(updated.contains("\r\n"));
        assertFalse(updated.replace("\r\n", "").contains("\n"));
    }

    @Test
    void 没有类声明时应该放到文件最前面() {
        String updated = TagWriter.withTags("// 只有注释\n", List.of("单调栈"));
        assertTrue(updated.startsWith("// @tag 单调栈"));
    }

    @Test
    void 写文件应该先生成备份(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("P0001TwoSum.java");
        Files.writeString(file, "class P0001TwoSum {\n}\n", StandardCharsets.UTF_8);

        TagWriter.ApplyResult first = TagWriter.applyToFile(file, List.of("哈希表"));
        assertTrue(first.changed());
        assertNotNull(first.backup());
        assertTrue(Files.exists(first.backup()));
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("// @tag 哈希表"));

        // 内容没变化时不再写、也不再产生新备份
        TagWriter.ApplyResult second = TagWriter.applyToFile(file, List.of("哈希表"));
        assertFalse(second.changed());
        assertNull(second.backup());
    }

    // ==================== 配置 ====================

    @Test
    void 密钥打印必须打码() {
        assertEquals("(未配置)", new AiConfig("", "x", "m", 0.3, 100, 10).maskedKey());
        assertEquals("****", new AiConfig("short", "x", "m", 0.3, 100, 10).maskedKey());

        String masked = new AiConfig("sk-1234567890abcdef", "x", "m", 0.3, 100, 10).maskedKey();
        assertTrue(masked.startsWith("sk-1"));
        assertTrue(masked.contains("****"));
        assertFalse(masked.contains("567890abc"));
    }

    @Test
    void 应该正确拼接接口地址() {
        assertEquals("https://api.deepseek.com/chat/completions",
                new AiConfig("k", "https://api.deepseek.com", "m", 0.3, 100, 10).chatUrl());
        assertEquals("https://api.deepseek.com/chat/completions",
                new AiConfig("k", "https://api.deepseek.com/", "m", 0.3, 100, 10).chatUrl());
        assertEquals("https://api.openai.com/v1/chat/completions",
                new AiConfig("k", "https://api.openai.com/v1", "m", 0.3, 100, 10).chatUrl());
    }

    @Test
    void 没有密钥时不应该可用() {
        assertFalse(new AiConfig("", "x", "m", 0.3, 100, 10).enabled());
        assertFalse(new AiConfig("  ", "x", "m", 0.3, 100, 10).enabled());
        assertTrue(new AiConfig("k", "x", "m", 0.3, 100, 10).enabled());
    }
}
