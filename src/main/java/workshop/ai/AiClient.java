package workshop.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 极简 LLM 客户端：一次「系统提示 + 用户输入」的同步对话。
 *
 * 只用 JDK 自带的 java.net.http.HttpClient，JSON 交给 Jackson（Spring Boot 已经带了）。
 * 生产环境还需要考虑：重试退避、限流、流式输出、token 计数——这里只做「够用且看得懂」的版本。
 */
public class AiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiConfig config;
    private final HttpClient http;

    public AiClient(AiConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean enabled() {
        return config.enabled();
    }

    public AiConfig config() {
        return config;
    }

    /**
     * 发一次对话请求，返回模型输出的纯文本。
     *
     * @throws AiException 未配置密钥、HTTP 非 200、返回体结构不符、超时等
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (!config.enabled()) {
            throw new AiException("未配置 API Key：请设置环境变量 AI_API_KEY，或在工作目录放 ai.properties");
        }

        String requestBody;
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", config.model());
            body.put("temperature", config.temperature());
            body.put("max_tokens", config.maxTokens());
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);
            requestBody = MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new AiException("构造请求体失败: " + e.getMessage(), e);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(config.chatUrl()))
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .header("Content-Type", "application/json")
                // 有些网关用 api-key 头，但 OpenAI 兼容接口统一用 Bearer
                .header("Authorization", "Bearer " + config.apiKey().trim())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AiException("请求失败（网络/超时）: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("请求被中断", e);
        }

        if (response.statusCode() != 200) {
            throw new AiException("接口返回 HTTP " + response.statusCode() + "：" + abbreviate(response.body(), 300));
        }

        try {
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new AiException("返回体里没有 choices[0].message.content：" + abbreviate(response.body(), 300));
            }
            return content.asText();
        } catch (IOException e) {
            throw new AiException("解析返回体失败: " + e.getMessage(), e);
        }
    }

    static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "...";
    }
}
