package workshop.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import workshop.Problem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProblemServiceTest {

    @TempDir
    Path temp;

    private void write(String relative, String content) throws IOException {
        Path file = temp.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private ProblemService service() throws IOException {
        write("dp/P0322CoinChange.java", "// @tag 完全背包\n// @tag 最值型\nclass P0322CoinChange {}\n");
        write("dp/P0279PerfectSquares.java", "// @tag 完全背包\nclass P0279PerfectSquares {}\n");
        write("array/P0001TwoSum.java", "class P0001TwoSum {}\n");
        write("common/ListNode.java", "class ListNode {}\n");
        return new ProblemService(temp);
    }

    @Test
    void 应该加载并筛选题目() throws IOException {
        ProblemService service = service();

        List<Problem> all = service.all();
        assertEquals(3, all.size());                       // ListNode 被过滤
        assertEquals(1, all.get(0).number());              // 按题号升序
        assertEquals(322, all.get(2).number());
    }

    @Test
    void 检索应该支持中文标签且空关键词返回全部() throws IOException {
        ProblemService service = service();

        assertEquals(3, service.search("").size());
        assertEquals(3, service.search(null).size());
        assertEquals(2, service.search("完全背包").size());
        assertEquals(1, service.search("322").size());
        assertEquals(0, service.search("不存在的关键词").size());
    }

    @Test
    void 相似题应该优先共享标签没有标签时退化为同主题() throws IOException {
        ProblemService service = service();

        assertEquals(1, service.similar(322).size());
        assertEquals(279, service.similar(322).get(0).number());
        assertEquals("tags", service.similarReason(322));

        // 1 号题没有标签，array 目录下也没有别的题 → 无推荐
        assertTrue(service.similar(1).isEmpty());
        assertEquals("none", service.similarReason(1));

        // 不存在的题号
        assertTrue(service.byNumber(9999).isEmpty());
        assertTrue(service.similar(9999).isEmpty());
    }

    @Test
    void 统计应该算对() throws IOException {
        ProblemService service = service();

        assertEquals(3, service.all().size());
        assertEquals(1, service.untaggedCount());
        assertEquals(67, service.tagCoveragePercent());     // 2/3 四舍五入
        assertEquals(2L, service.countByTopic().get("dp"));
        assertEquals(1L, service.countByTopic().get("array"));
        assertEquals(List.of(1), service.untaggedNumbers());
    }

    @Test
    void 重新扫描应该拿到最新数据() throws IOException {
        ProblemService service = service();
        assertEquals(3, service.all().size());

        write("dp/P0139WordBreak.java", "// @tag 完全背包\nclass P0139WordBreak {}\n");

        assertEquals(3, service.all().size());              // 还是缓存
        assertEquals(4, service.reload().size());           // 重新扫描后 4 题
        assertEquals(3, service.search("完全背包").size());
    }

    @Test
    void 未配置目录时应该自动探测() {
        assertNotNull(ProblemService.resolveRoot(""));
        assertNotNull(ProblemService.resolveRoot(null));
        assertEquals(Path.of("D:\\my-leetcode"),
                ProblemService.resolveRoot("D:\\my-leetcode"));
    }
}
