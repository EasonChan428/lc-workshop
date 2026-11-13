package workshop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}