package workshop.ai;

/** AI 调用相关的异常（密钥错、限流、超时、返回格式不对都会抛它） */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
