package workshop;

public class IndexInjector {

    public static final String START_MARKER = "<!-- INDEX:START -->";
    public static final String END_MARKER = "<!-- INDEX:END -->";

    /**
     * 把 original 里两个标记之间的内容替换成 newSection。
     * 标记缺失（或顺序颠倒）时抛 IllegalArgumentException，绝不瞎改。
     */
    public static String replaceSection(String original, String newSection) {
        int start = original.indexOf(START_MARKER);
        int end = original.indexOf(END_MARKER);
        if (start < 0 || end < 0 || end < start) {
            throw new IllegalArgumentException(
                    "缺少索引标记，请确认文件里有 " + START_MARKER + " 和 " + END_MARKER);
        }
        // 保留标记本身，只换中间的正文
        String head = original.substring(0, start + START_MARKER.length());
        String tail = original.substring(end);
        return head + "\n" + newSection + tail;
    }
}