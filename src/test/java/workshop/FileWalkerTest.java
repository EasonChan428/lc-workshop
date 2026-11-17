package workshop;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileWalkerTest {

    // ==================== 文件名解析 ====================

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
    void 连续大写字母不应该被拆开() {
        assertEquals("Jump Game II", FileWalker.extractTitle("P0045JumpGameII.java"));
        assertEquals("Two Sum", FileWalker.extractTitle("P0001TwoSum.java"));
        assertEquals("Longest Common Subsequence",
                FileWalker.extractTitle("P1143LongestCommonSubsequence.java"));
    }

    @Test
    void 应该能从路径提取主题() {
        assertEquals("dp",
                FileWalker.extractTopic(Path.of("C:\\x\\leetcode\\dp\\P0279PerfectSquares.java")));
    }

    // ==================== 索引生成 ====================

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

    // ==================== README 标记替换 ====================

    @Test
    void 应该只替换标记之间的内容() {
        String original = "# 标题\n<!-- INDEX:START -->\n旧内容\n<!-- INDEX:END -->\n结尾\n";
        String updated = IndexInjector.replaceSection(original, "| 新表格 |\n");

        assertTrue(updated.contains("| 新表格 |"));
        assertFalse(updated.contains("旧内容"));
        assertTrue(updated.startsWith("# 标题"));
        assertTrue(updated.endsWith("结尾\n"));
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
        assertEquals(once, twice);
    }

    // ==================== 参数解析 ====================

    private static Main.Defaults defaults() {
        return new Main.Defaults(Path.of("D:\\repo"), Path.of("R.md"));
    }

    @Test
    void 不传参数时应该用默认值() {
        Main.Options o = Main.parseArgs(new String[]{}, defaults());

        assertEquals("index", o.command());
        assertEquals(Path.of("D:\\repo"), o.root());
        assertEquals(Path.of("R.md"), o.readme());
        assertFalse(o.write());
    }

    @Test
    void 传了参数和标志时应该正确解析() {
        // 路径 + 标志混在一起，顺序不影响结果
        Main.Options o = Main.parseArgs(new String[]{"D:\\other", "docs\\r.md", "--write"}, defaults());
        assertEquals(Path.of("D:\\other"), o.root());
        assertEquals(Path.of("docs\\r.md"), o.readme());
        assertTrue(o.write());

        // 只传一个路径：README 用默认值，标志照样生效
        Main.Options o2 = Main.parseArgs(new String[]{"--write", "D:\\only"}, defaults());
        assertEquals(Path.of("D:\\only"), o2.root());
        assertEquals(Path.of("R.md"), o2.readme());
        assertTrue(o2.write());
    }

    @Test
    void 子命令应该能被正确识别() {
        // find：关键词 + 可选目录
        Main.Options find = Main.parseArgs(new String[]{"find", "dp"}, defaults());
        assertEquals("find", find.command());
        assertEquals("dp", find.keyword());
        assertEquals(Path.of("D:\\repo"), find.root());

        // similar：关键词 + 显式目录
        Main.Options similar = Main.parseArgs(new String[]{"similar", "322", "D:\\other"}, defaults());
        assertEquals("similar", similar.command());
        assertEquals("322", similar.keyword());
        assertEquals(Path.of("D:\\other"), similar.root());

        // stats：只有目录
        Main.Options stats = Main.parseArgs(new String[]{"stats", "D:\\other"}, defaults());
        assertEquals("stats", stats.command());
        assertEquals(Path.of("D:\\other"), stats.root());

        // 第一个参数不是命令 → 仍然当 index 的目录
        Main.Options justPath = Main.parseArgs(new String[]{"C:\\some\\dir"}, defaults());
        assertEquals("index", justPath.command());
        assertEquals(Path.of("C:\\some\\dir"), justPath.root());
    }

    // ==================== 题目对象与检索 ====================

    @Test
    void 应该能把文件解析成题目对象并按题号排序() {
        List<Problem> ps = FileWalker.parseProblems(List.of(
                Path.of("dp/P0322CoinChange.java"),
                Path.of("array/P0001TwoSum.java"),
                Path.of("common/ListNode.java")));

        assertEquals(2, ps.size());                     // ListNode 被过滤掉
        assertEquals(1, ps.get(0).number());
        assertEquals("Two Sum", ps.get(0).title());
        assertEquals("array", ps.get(0).topic());
        assertEquals(322, ps.get(1).number());          // 按题号升序
        assertTrue(ps.get(0).tags().isEmpty());         // 默认没有标签
    }

    @Test
    void 检索应该匹配题号标题主题且忽略大小写() {
        List<Problem> all = FileWalker.parseProblems(List.of(
                Path.of("dp/P0279PerfectSquares.java"),
                Path.of("array/P0001TwoSum.java"),
                Path.of("dp/P0322CoinChange.java")));

        assertEquals(1, FileWalker.search(all, "279").size());
        assertEquals(1, FileWalker.search(all, "perfect squares").size());
        assertEquals(2, FileWalker.search(all, "DP").size());
        assertEquals(0, FileWalker.search(all, "matrix").size());
    }

    @Test
    void 检索不应该因文件名拼接产生假阳性() {
        // "validparentheses" 去掉空格后含 "dp"，但不该被 "dp" 命中
        List<Problem> all = FileWalker.parseProblems(List.of(
                Path.of("stack/P0020ValidParentheses.java"),
                Path.of("dp/P0279PerfectSquares.java")));

        assertEquals(1, FileWalker.search(all, "dp").size());
        assertEquals(279, FileWalker.search(all, "dp").get(0).number());
    }

    // ==================== 标签与相似题 ====================

    @Test
    void 应该能从注释里解析标签() {
        assertEquals(List.of("完全背包", "最值型"), FileWalker.parseTags(List.of(
                "package workshop;",
                "",
                "// @tag 完全背包",
                "// @tag 最值型",
                "class P0322CoinChange {")));

        // 类声明之后的 @tag 不该被读到（注释块里的示例代码）
        assertEquals(List.of("区间DP"), FileWalker.parseTags(List.of(
                "// @tag 区间DP",
                "class A {",
                "// @tag 这是类里面的，不算")));

        // 容忍 //@tag 这种没空格的写法
        assertEquals(List.of("双指针"), FileWalker.parseTags(List.of("//@tag 双指针", "class A {")));

        assertEquals(List.of(), FileWalker.parseTags(List.of("class A {")));
    }

    @Test
    void 应该能按中文标签检索并推荐相似题() {
        List<Problem> base = FileWalker.parseProblems(List.of(
                Path.of("dp/P0279PerfectSquares.java"),
                Path.of("dp/P0322CoinChange.java"),
                Path.of("dp/P0139WordBreak.java"),
                Path.of("array/P0001TwoSum.java")));

        Map<String, List<String>> tags = Map.of(
                "P0279PerfectSquares.java", List.of("完全背包"),
                "P0322CoinChange.java", List.of("完全背包", "最值型"),
                "P0139WordBreak.java", List.of("可行性"));
        List<Problem> all = FileWalker.applyTags(base, tags);

        // 中文标签检索
        assertEquals(2, FileWalker.search(all, "完全背包").size());

        // 相似题：322 有「完全背包 + 最值型」，只有 279 共享
        Problem target = FileWalker.search(all, "322").get(0);
        List<Problem> sims = FileWalker.similar(target, all);
        assertEquals(1, sims.size());
        assertEquals(279, sims.get(0).number());
        assertEquals(1, sims.get(0).sharedTagCount(target));
    }

    @Test
    void 没有标签时相似题应退化为同主题() {
        List<Problem> all = FileWalker.parseProblems(List.of(
                Path.of("dp/P0279PerfectSquares.java"),
                Path.of("dp/P0322CoinChange.java"),
                Path.of("array/P0001TwoSum.java")));

        Problem target = FileWalker.search(all, "279").get(0);
        assertEquals(0, FileWalker.similar(target, all).size());     // 没标签 → 空

        List<Problem> sameTopic = FileWalker.sameTopic(target, all);
        assertEquals(1, sameTopic.size());
        assertEquals(322, sameTopic.get(0).number());
    }

    // ==================== 统计 ====================

    @Test
    void 统计应该正确计算主题分布与标签覆盖率() {
        List<Problem> all = FileWalker.applyTags(
                FileWalker.parseProblems(List.of(
                        Path.of("dp/P0279PerfectSquares.java"),
                        Path.of("dp/P0322CoinChange.java"),
                        Path.of("array/P0001TwoSum.java"),
                        Path.of("tree/P0094BinaryTreeInorderTraversal.java"))),
                Map.of("P0322CoinChange.java", List.of("完全背包"),
                        "P0001TwoSum.java", List.of("哈希表")));

        assertEquals(2L, FileWalker.countByTopic(all).get("dp"));
        assertEquals(1L, FileWalker.countByTopic(all).get("array"));
        assertEquals(50, FileWalker.tagCoveragePercent(all));         // 2 / 4
        assertEquals(2, FileWalker.problemsWithoutTags(all).size());
        assertEquals(1L, FileWalker.tagFrequency(all).get("完全背包"));
        assertEquals(0, FileWalker.tagCoveragePercent(List.of()));    // 空列表不炸
    }
}
