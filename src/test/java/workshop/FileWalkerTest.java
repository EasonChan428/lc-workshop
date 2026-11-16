package workshop;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileWalkerTest {

    @Test
    void 应该能解析出题号() {
        assertEquals(1, FileWalker.parseNumber("P0001TwoSum.java"));
        assertEquals(5, FileWalker.parseNumber("P0005LongestPalindromicSubstring.java"));
        assertEquals(279, FileWalker.parseNumber("P0279PerfectSquares.java"));
        assertEquals(1143, FileWalker.parseNumber("P1143LongestCommonSubsequence.java"));
    }

    @Test
    void 非题目文件应返回负一() {
        assertEquals(-1, FileWalker.parseNumber("ListNode.java"));
        assertEquals(-1, FileWalker.parseNumber("TreeNode.java"));
        assertEquals(-1, FileWalker.parseNumber("P27X9Bad.java"));
        assertEquals(-1, FileWalker.parseNumber("P000"));
        assertEquals(-1, FileWalker.parseNumber(null));
    }

    @Test
    void 应该能从文件名提取标题() {
        assertEquals("Two Sum", FileWalker.extractTitle("P0001TwoSum.java"));
        assertEquals("Perfect Squares", FileWalker.extractTitle("P0279PerfectSquares.java"));
        assertEquals("Longest Common Subsequence",
                FileWalker.extractTitle("P1143LongestCommonSubsequence.java"));
        assertEquals("", FileWalker.extractTitle("ListNode.java"));
    }

    @Test
    void 索引行格式应该正确() {
        assertEquals("| 279 | Perfect Squares | P0279PerfectSquares.java |",
                FileWalker.buildRow(Path.of("dp/P0279PerfectSquares.java")));
    }

    @Test
    void 索引应按题号排序并跳过非题目文件() {
        List<Path> files = List.of(
                Path.of("dp/P0322CoinChange.java"),
                Path.of("array/P0001TwoSum.java"),
                Path.of("common/ListNode.java"),
                Path.of("dp/P0279PerfectSquares.java"));
        String index = FileWalker.buildIndex(files);

        assertTrue(index.contains("| 1 | Two Sum |"));
        assertTrue(index.indexOf("| 279 |") < index.indexOf("| 322 |"));   // 按题号升序
        assertFalse(index.contains("ListNode"));                           // 非题目文件被跳过
    }

    @Test
    void 不传参数时应该用默认值() {
        Main.Options o = Main.parseArgs(new String[]{});
        assertEquals(Main.DEFAULT_ROOT, o.root());
        assertEquals(Main.DEFAULT_README, o.readme());
        assertFalse(o.write());
    }

    @Test
    void 传了参数和标志时应该正确解析() {
        // 路径 + 标志混在一起，顺序不影响结果
        Main.Options o = Main.parseArgs(new String[]{"D:\\other", "docs\\r.md", "--write"});
        assertEquals(Path.of("D:\\other"), o.root());
        assertEquals(Path.of("docs\\r.md"), o.readme());
        assertTrue(o.write());

        // 只传一个路径：README 用默认值，标志照样生效
        Main.Options o2 = Main.parseArgs(new String[]{"--write", "D:\\only"});
        assertEquals(Path.of("D:\\only"), o2.root());
        assertEquals(Main.DEFAULT_README, o2.readme());
        assertTrue(o2.write());
    }

    @Test
    void 应该只替换标记之间的内容() {
        String original = "# 标题\n<!-- INDEX:START -->\n旧内容\n<!-- INDEX:END -->\n结尾\n";
        String updated = IndexInjector.replaceSection(original, "| 新表格 |\n");

        assertTrue(updated.contains("| 新表格 |"));
        assertFalse(updated.contains("旧内容"));          // 旧内容被换掉
        assertTrue(updated.startsWith("# 标题"));          // 标记之前原样保留
        assertTrue(updated.endsWith("结尾\n"));            // 标记之后原样保留
    }

    @Test
    void 缺少标记时应抛异常() {
        assertThrows(IllegalArgumentException.class,
                () -> IndexInjector.replaceSection("# 没有标记的文件\n", "x\n"));
    }

    @Test
    void 重复替换应该是幂等的() {
        String original = "# T\n<!-- INDEX:START -->\n旧\n<!-- INDEX:END -->\nT\n";
        String once = IndexInjector.replaceSection(original, "新\n");
        String twice = IndexInjector.replaceSection(once, "新\n");
        assertEquals(once, twice);                          // 再跑一次结果不变
    }
}