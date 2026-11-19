package workshop.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 让模型读题解代码，提炼 1~3 个「解法模式」标签。
 *
 * 重点在 parseTags：LLM 的输出不可靠（可能带 markdown 围栏、可能多说话、可能编标签），
 * 所以「调用」和「解析校验」分开写，解析部分是不依赖网络的纯函数，可以充分测试。
 */
public class TagSuggester {

    static final int MAX_TAGS = 3;
    static final int MAX_TAG_LENGTH = 12;
    static final int MAX_CODE_CHARS = 4000;

    static final String SYSTEM_PROMPT = """
            你是一个 LeetCode 解法标注助手。用户会给你一道题的 Java 解法代码。
            请提炼这道题所用到的「解法模式」标签，用于跨题目检索与相似题推荐。

            要求：
            1. 输出 1~3 个标签，每个是简短中文术语（2~6 个字），例如：完全背包、01背包、区间DP、双状态、快慢指针、单调栈、二分答案、记忆化搜索。
            2. 优先描述算法技巧或状态设计，不要输出「数组」「字符串」「哈希表」这类数据结构名。
            3. 只输出一个 JSON 字符串数组，例如 ["完全背包","最值型"]。
            4. 不要输出解释、不要用 markdown 代码块、不要加序号。
            """;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiClient client;

    public TagSuggester(AiClient client) {
        this.client = client;
    }

    public boolean enabled() {
        return client != null && client.enabled();
    }

    /** 需要网络：返回模型建议的标签（已解析、去重、限量） */
    public List<String> suggest(int number, String title, String topic, String code) {
        String raw = client.chat(SYSTEM_PROMPT, buildUserPrompt(number, title, topic, code));
        return parseTags(raw);
    }

    static String buildUserPrompt(int number, String title, String topic, String code) {
        String trimmedCode = code == null ? "" : code;
        if (trimmedCode.length() > MAX_CODE_CHARS) {
            trimmedCode = trimmedCode.substring(0, MAX_CODE_CHARS) + "\n// ...（代码过长已截断）";
        }
        return """
                题号：%d
                标题：%s
                主题目录：%s

                ```java
                %s
                ```
                """.formatted(number, title, topic, trimmedCode);
    }

    /**
     * 解析模型输出为标签列表（纯函数）。
     * 依次尝试：去掉 ``` 围栏 → 当 JSON 数组解析 → 抽出第一个 [...] → 按行/逗号切分。
     */
    public static List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String text = raw.trim();

        // 1) 去掉 markdown 代码围栏
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd > 0) {
                text = text.substring(firstLineEnd + 1);
            }
            int fence = text.lastIndexOf("```");
            if (fence >= 0) {
                text = text.substring(0, fence);
            }
            text = text.trim();
        }

        // 2) 直接当 JSON 数组解析
        List<String> fromJson = tryParseJsonArray(text);
        if (fromJson != null) {
            return normalize(fromJson);
        }

        // 3) 从文本里抽出第一个 [...] 再试
        int left = text.indexOf('[');
        int right = text.lastIndexOf(']');
        if (left >= 0 && right > left) {
            fromJson = tryParseJsonArray(text.substring(left, right + 1));
            if (fromJson != null) {
                return normalize(fromJson);
            }
        }

        // 4) 兜底：按行 / 逗号切分 —— 只在输出看起来像「列表」时才采用，
        //    否则一句「我不知道」会被当成标签写进文件
        boolean looksLikeList = text.contains("\n")
                || text.contains(",")
                || text.contains("，")
                || text.contains("、")
                || text.matches("(?s).*^\\s*[-*•].*");
        if (!looksLikeList) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String piece : text.split("[\\n,，、]")) {
            items.add(piece);
        }
        return normalize(items);
    }

    private static List<String> tryParseJsonArray(String text) {
        if (!text.startsWith("[")) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(text);
            if (!node.isArray()) {
                return null;
            }
            List<String> result = new ArrayList<>();
            for (JsonNode item : node) {
                result.add(item.asText());
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    /** 清洗：去项目符号、引号、多余空白；忽略超长项；去重（忽略大小写）；限量 */
    static List<String> normalize(List<String> raw) {
        List<String> result = new ArrayList<>();
        for (String item : raw) {
            String tag = cleanOne(item);
            if (tag.isEmpty()) {
                continue;
            }
            boolean duplicated = result.stream().anyMatch(existing -> existing.equalsIgnoreCase(tag));
            if (duplicated) {
                continue;
            }
            result.add(tag);
            if (result.size() >= MAX_TAGS) {
                break;
            }
        }
        return result;
    }

    private static String cleanOne(String item) {
        if (item == null) {
            return "";
        }
        String tag = item.trim()
                .replaceAll("^[-*•]\\s*", "")            // 去掉 "- "、"* " 这类项目符号
                .replaceAll("^\\d+[.)、]\\s*", "")       // 去掉 "1. "、"2)" 这类编号
                .replaceAll("^[\"'`「『]+|[\"'`」』]+$", "") // 去掉包裹的引号
                .replaceAll("\\s+", "");                 // 标签内部不留空格
        if (tag.startsWith("@tag")) {
            tag = tag.substring(4).trim();
        }
        return tag.length() > MAX_TAG_LENGTH ? "" : tag;
    }
}
