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
        assertEquals(Main.DEFAULT_ROOT, Main.resolveRoot(new String[]{}));
        assertEquals(Main.DEFAULT_OUT, Main.resolveOut(new String[]{}));
    }

    @Test
    void 传了参数时应该用传入的值() {
        assertEquals(Path.of("D:\\other"), Main.resolveRoot(new String[]{"D:\\other"}));
        assertEquals(Path.of("out.md"), Main.resolveOut(new String[]{"in.md", "out.md"}));
        // 只传一个参数时，输出仍用默认值
        assertEquals(Main.DEFAULT_OUT, Main.resolveOut(new String[]{"in.md"}));
    }
}