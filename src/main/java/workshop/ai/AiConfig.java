package workshop.ai;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * AI 接入配置。
 *
 * 读取优先级：环境变量 > ai.properties 文件 > 默认值
 *   环境变量：AI_API_KEY / AI_BASE_URL / AI_MODEL
 *   文件：工作目录下的 ai.properties（已在 .gitignore 中，不会进 Git）
 *
 * 任何厂商只要提供 OpenAI 兼容接口都能用，换厂商只需要改 base-url 与 model：
 *   DeepSeek  https://api.deepseek.com                              deepseek-chat
 *   通义千问   https://dashscope.aliyuncs.com/compatible-mode/v1     qwen-plus
 *   智谱 GLM   https://open.bigmodel.cn/api/paas/v4                  glm-4-flash
 *   OpenAI     https://api.openai.com/v1                            gpt-4o-mini
 */
public record AiConfig(String apiKey, String baseUrl, String model,
                       double temperature, int maxTokens, int timeoutSeconds) {

    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    public static final String DEFAULT_MODEL = "deepseek-chat";
    public static final String CONFIG_FILE = "ai.properties";

    /** 是否配置了密钥（没配就整体降级为「AI 未启用」） */
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 拼接 chat/completions 地址，兼容 base 带不带 /v1 的情况 */
    public String chatUrl() {
        String base = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        return base + "/chat/completions";
    }

    /** 打印用：永远不要输出完整密钥 */
    public String maskedKey() {
        if (!enabled()) {
            return "(未配置)";
        }
        String k = apiKey.trim();
        if (k.length() <= 8) {
            return "****";
        }
        return k.substring(0, 4) + "****" + k.substring(k.length() - 4)
                + "（长度 " + k.length() + "）";
    }

    public static AiConfig load() {
        Properties fileProps = readFile();

        String key = firstNonBlank(
                System.getenv("AI_API_KEY"),
                System.getProperty("AI_API_KEY"),
                fileProps.getProperty("key"));

        String baseUrl = firstNonBlank(
                System.getenv("AI_BASE_URL"),
                System.getProperty("AI_BASE_URL"),
                fileProps.getProperty("base-url"),
                DEFAULT_BASE_URL);

        String model = firstNonBlank(
                System.getenv("AI_MODEL"),
                System.getProperty("AI_MODEL"),
                fileProps.getProperty("model"),
                DEFAULT_MODEL);

        return new AiConfig(
                key == null ? "" : key.trim(),
                baseUrl,
                model,
                parseDouble(fileProps.getProperty("temperature"), 0.3),
                parseInt(fileProps.getProperty("max-tokens"), 300),
                parseInt(fileProps.getProperty("timeout-seconds"), 60));
    }

    private static Properties readFile() {
        Properties props = new Properties();
        Path file = Path.of(CONFIG_FILE);
        if (!Files.isRegularFile(file)) {
            return props;
        }
        try (var in = Files.newInputStream(file)) {
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("警告: 读取 " + CONFIG_FILE + " 失败 -> " + e.getMessage());
        }
        return props;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static double parseDouble(String v, double fallback) {
        try {
            return v == null ? fallback : Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(String v, int fallback) {
        try {
            return v == null ? fallback : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
