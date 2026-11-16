package workshop;

/**
 * 一道题的信息。
 * record 会自动生成：构造器、number()/title()/topic()/fileName() 取值方法、equals、toString。
 */
public record Problem(int number, String title, String topic, String fileName) {
}