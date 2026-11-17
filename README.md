# 题解工坊（lc-workshop）

一个面向个人刷题场景的命令行工具：**自动维护题解索引、按概念检索题目、推荐相似题**。

Java 21 · Maven · JUnit 5 · 零运行时依赖 · 单个可执行 jar

---

## 为什么做这个

刷够 100 道题之后，真正花时间的不是解题，而是这些琐事：

| 痛点 | 以前怎么做 | 现在 |
|---|---|---|
| 每加一道题，要手动往 README 索引表和目录树里插一行 | 手敲 Markdown，容易漏、容易错位 | `index --write` 自动生成（幂等、有备份） |
| 想找「哪些题是完全背包」只能翻目录 | 凭记忆 | `find 完全背包` |
| 复习时不知道该接着做哪道 | 靠感觉 | `similar 322` |
| 改完一题不知道会不会连带弄坏别的 | 手动跑 `javac` | `mvn test` + 全量编译 |

工具的核心思路：**元数据（标签）就写在题解源码的注释里**，离代码最近，永远不会和代码不同步。

---

## 功能

| 命令 | 作用 |
|---|---|
| `index` | 扫描题库，生成 Markdown 索引表，**只替换 README 里 `<!-- INDEX:START -->` 与 `<!-- INDEX:END -->` 之间的内容** |
| `find <关键词>` | 按题号 / 标题 / 主题 / 标签检索 |
| `similar <题号或关键词>` | 推荐共享标签的相似题（该题没标签时退化为同主题推荐） |
| `stats` | 主题分布、标签覆盖率、标签频次、未打标签清单 |

---

## 快速开始

```bash
mvn clean package
java -jar target/lc-workshop-0.1.0-SNAPSHOT.jar stats
```

Windows 上也可以直接双击：

- `run.bat` —— 用默认参数运行（**把文件夹拖到它上面**即可扫描该目录）
- `index.bat` —— 一键更新 README 索引（等价于 `index --write`）

### 用法示例

```bash
# 1. 预览：只打印摘要，不改任何文件
java -jar lc-workshop.jar

# 2. 真正写入（会先备份 README.md.bak）
java -jar lc-workshop.jar --write

# 3. 指定题库目录和 README
java -jar lc-workshop.jar index "D:/my-leetcode/src/leetcode" "D:/my-leetcode/README.md" --write

# 4. 检索
java -jar lc-workshop.jar find 完全背包
java -jar lc-workshop.jar find dp
java -jar lc-workshop.jar find 279

# 5. 相似题推荐
java -jar lc-workshop.jar similar 322

# 6. 统计
java -jar lc-workshop.jar stats
```

`similar 322` 的输出示例：

```
基准题目: 322  Coin Change   主题: dp   标签: 完全背包,最值型
共享标签的相似题 2 道：
  279  dp   Perfect Squares   共享 2 个: 完全背包,最值型
  139  dp   Word Break        共享 1 个: 完全背包
```

---

## 标签规范

在题解文件的 **`class` 声明之前**写 `// @tag` 行（可以写多行）：

```java
// @tag 完全背包
// @tag 最值型
class P0322CoinChange {
    ...
}
```

- 解析器读到 `class` 声明就停止，所以文件后面被注释掉的备选解法不会被误读；
- 标签名建议用**解法模式**而不是题目主题：`完全背包`、`01背包`、`区间DP`、`双状态`、`快慢指针`、`单调栈`……
  这类标签才能把不同目录的题串起来（例如 142 环形链表和 287 寻找重复数是同一招）。

---

## 配置

优先级：**命令行参数 > 配置文件 > 自动探测**

复制 `lc-workshop.properties.example` 为 `lc-workshop.properties`（放在 jar 同级目录或用户主目录）：

```properties
root=D:/my-leetcode/src/leetcode
readme=D:/my-leetcode/README.md
```

不配置时自动探测：当前目录下的 `src/leetcode`，否则用当前目录。

---

## 安全机制

写文件是破坏性操作，所以这个工具默认**什么都不改**：

| 机制 | 说明 |
|---|---|
| 默认预览 | 不加 `--write` 就只打印摘要，不写文件 |
| 幂等 | 内容没变化时直接跳过，不覆盖文件、不刷新修改时间 |
| 自动备份 | 写入前生成 `README.md.bak` |
| 锚点校验 | README 缺少 `<!-- INDEX:... -->` 标记时抛异常退出，绝不猜位置 |
| 拒绝空写入 | 一个题目都没扫到时拒绝写入（多半是路径给错了） |

**退出码**：`0` 成功 / `1` 环境或输入错误（目录、文件不存在）/ `2` 用法错误（参数不对、没有题目）。

---

## 项目结构

```
src/main/java/workshop/
├── Main.java           入口：参数解析、子命令分发、IO
├── FileWalker.java     纯逻辑：文件名解析、标签解析、检索、相似题、统计
├── IndexInjector.java  只替换 README 的标记区间
└── Problem.java        领域对象（record）：题号/标题/主题/文件名/标签
src/test/java/workshop/
└── FileWalkerTest.java 20 个单元测试
```

设计上刻意把「能测的」和「不能测的」分开：**除了 `findJavaFiles` / `extractTags` 和 `Main` 里的写法逻辑，其余全是纯函数**，所以 20 个测试几乎不需要碰磁盘。

---

## 测试

```bash
mvn test
```

覆盖：题号/标题解析、索引排序与过滤、README 标记替换与幂等、命令行参数解析、标签解析、检索（含「文件名拼接产生假阳性」的回归测试）、相似题与降级推荐、统计指标。

---

## 已知限制 / 后续计划

- **索引表格式是简化版 3 列**（题号 / 标题 / 文件）。如果原 README 的索引表是列数更多的自定义格式（例如还带难度、分类、备注），**先用预览模式比对，不要直接 `--write`**，否则会被覆盖成 3 列；
- 连续大写缩写词断词不完美（`LRUCache` 不会拆成 `LRU Cache`）；
- 标签需要手工维护，覆盖率靠 `stats` 逐步补齐；
- **v2 计划**：Web 界面（Spring Boot）+ AI 辅助（自动建议标签、按题意检索）。

---

## License

MIT
