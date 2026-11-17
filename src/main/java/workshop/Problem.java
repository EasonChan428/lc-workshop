package workshop;

import java.util.List;

/**
 * 一道题的信息。
 * record 会自动生成构造器、number()/title()/topic()/fileName()/tags() 取值方法、equals 和 toString。
 */
public record Problem(int number, String title, String topic, String fileName, List<String> tags) {

    /** 紧凑构造器：防御性拷贝，顺手把 null 变成空列表，避免调用方踩坑 */
    public Problem {
        tags = (tags == null) ? List.of() : List.copyOf(tags);
    }

    /** 和另一道题共享的标签个数（用于相似题排序） */
    public int sharedTagCount(Problem other) {
        return (int) tags.stream().filter(other.tags::contains).count();
    }

    /** 是否打过标签 */
    public boolean hasTags() {
        return !tags.isEmpty();
    }
}
