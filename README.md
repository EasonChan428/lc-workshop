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

### Web 界面（v2）

打包后会得到**两个 jar**：

| 产物 | 用途 |
|---|---|
| `lc-workshop-0.1.0-SNAPSHOT.jar` | 命令行工具（`java -jar` 仍是 CLI） |
| `lc-workshop-0.1.0-SNAPSHOT-web.jar` | 可独立运行的 Web 端（内嵌 Tomcat，**不需要装 Maven**） |

最简单的启动方式：

```bash
mvn clean package                 # 打出两个 jar
java -jar target/lc-workshop-0.1.0-SNAPSHOT-web.jar
```

Windows 上直接**双击 `web.bat`**（也可把题库文件夹拖到它上面指定目录），启动后会自动打开浏览器到 <http://localhost:8080>。

开发时（改代码即时生效）用：`mvn compile exec:exec`。

**题库目录可以在页面上选**：点「浏览…」→ 服务端目录树里逐级点选 → 「使用此目录」。选过的目录会记在 `.lc-workshop-root` 里，下次启动自动沿用。（浏览器的 JS 无法获取真实文件路径，所以目录浏览由后端提供。）

| 接口 | 说明 |
|---|---|
| `GET /api/problems?q=关键词` | 题目列表（题号 / 标题 / 主题 / 标签检索） |
| `GET /api/problems/{number}` | 单道题，不存在返回 404 |
| `GET /api/problems/{number}/similar` | 相似题推荐，`reason` 为 `tags` / `topic` / `none` |
| `GET /api/problems/{number}/code` | 单题源码（只读，用于复习） |
| `GET /api/stats` | 主题分布、标签频次、覆盖率、未打标签题号 |
| `GET /api/dirs?path=…` | 浏览服务端目录（选择题库用） |
| `GET /api/roots` | 常见入口：盘符 / 用户目录 / 当前目录 |
| `POST /api/root` | 切换题库目录 `{"path": "…"}` |
| `POST /api/reload` | 重新扫描磁盘 |
| `GET /api/ai/status` | AI 是否可用（密钥永远只返回掩码） |
| `POST /api/ai/suggest-tags/{number}` | 让模型建议解法标签（**只建议，不写文件**） |
| `POST /api/ai/apply-tags/{number}` | 把人工确认后的标签写回源码 `{"tags": [...]}`，自动备份 |

页面支持：结果表格、按主题筛选、点标签直接检索、「代码」按钮查看源码（行号 + 轻量 Java 高亮）、「AI 建议标签」按钮、切换题库目录。

### AI 辅助标注标签

点「代码」→ 面板里「AI 建议标签」→ 模型读完代码给出 1~3 个解法标签 → 点选确认 → 「写回文件」（自动生成 `.bak` 备份）。

**配置**（二选一，密钥永不入库、打印时自动打码）：

```bash
# 方式一：本地文件（推荐本地开发用）
cp ai.properties.example ai.properties   # 然后填入 key

# 方式二：环境变量（优先级更高，适合 CI）
set AI_API_KEY=sk-xxxx
set AI_BASE_URL=https://api.deepseek.com
set AI_MODEL=deepseek-chat
```

任何 **OpenAI 兼容**的模型服务都能用，换厂商只改 `base-url` 与 `model`：

| 厂商 | base-url | model 示例 |
|---|---|---|
| DeepSeek | `https://api.deepseek.com` | `deepseek-chat` |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` |
| 智谱 GLM | `https://open.bigmodel.cn/api/paas/v4` | `glm-4-flash` |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` |

**为什么不直接让 AI 改文件**：模型输出不可靠（可能带 markdown 围栏、可能编造标签），所以流程强制「模型只提建议 → 人确认 → 程序写回」。解析与校验（`TagSuggester.parseTags`）和写回（`TagWriter`）都是纯函数，有专门的离线测试覆盖。

> **安全默认**：Web 端只监听 `127.0.0.1`（本机），因为这个工具会读取本地文件系统。想给局域网其他设备用，把 `application.properties` 里的 `server.address` 改成 `0.0.0.0` 并自行承担风险。

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
├── Main.java               CLI 入口：参数解析、子命令分发、IO
├── FileWalker.java         纯逻辑：文件名解析、标签解析、检索、相似题、统计
├── IndexInjector.java      只替换 README 的标记区间
├── Problem.java            领域对象（record）：题号/标题/主题/文件名/标签
└── web/
    ├── LcWorkshopApplication.java  Web 入口（Spring Boot）
    ├── ProblemService.java         加载缓存题库，复用 FileWalker
    └── ProblemController.java      REST 接口
src/main/resources/
├── application.properties         端口与题库目录配置
└── static/index.html              单页前端（原生 JS，无构建步骤）
src/test/java/workshop/
├── FileWalkerTest.java            20 个单元测试
└── web/ProblemServiceTest.java    6 个服务层测试
```

设计上刻意把「能测的」和「不能测的」分开：**除了 `findJavaFiles` / `extractTags` 和 `Main` 里的写法逻辑，其余全是纯函数**，所以测试几乎不需要碰磁盘。

---

## 测试

```bash
mvn test
```

覆盖：题号/标题解析、索引排序与过滤、README 标记替换与幂等、命令行参数解析、标签解析、检索（含「文件名拼接产生假阳性」的回归测试）、相似题与降级推荐、统计指标、源码读取与题库目录切换。

---

## 路线图

已完成：CLI（索引 / 检索 / 相似题 / 统计）、Web 端（页面选题库 + 查看源码）、可独立运行的 web jar。

待办按价值排序（`↑` 表示优先级更高）：

| 待办 | 说明 | 状态 |
|---|---|---|
| **AI 生成标签** | 把题解代码交给模型，输出 3 个解法标签 → 页面人工确认 → 复用「预览 + 备份 + 幂等」写回 `// @tag` | 计划中（v2.4，最高优先） |
| 代码面板「上一条 / 下一条」 | 连续复习时不用关掉面板 | 已记录，待做 |
| 代码面板里高亮搜索关键词 | 搜「完全背包」时直接在源码里标出来 | 已记录，待做 |
| `@tag` 行视觉强调 | 一眼看到这道题的核心技巧（依赖 AI 生成的标签更准） | 已记录，待做 |
| 索引表模板可配置 | 让 `index` 能写回列数更多的自定义表格（难度 / 分类 / 备注） | 已记录，待做 |
| 缩写词断词 | `LRUCache` → `LRU Cache` | 已知限制 |

## 已知限制

- **索引表格式是简化版 3 列**（题号 / 标题 / 文件）。如果原 README 的索引表列数更多（还带难度、分类、备注），**先用预览模式比对，不要直接 `--write`**，否则会被覆盖成 3 列；
- 连续大写缩写词断词不完美（`LRUCache` 不会拆成 `LRU Cache`）；
- 标签目前需要手工维护，覆盖率靠 `stats` 逐步补齐（后续由 AI 生成解决）；
- Web 端只监听本机，不提供网页编辑（改代码请用 IDE）。

---

## 安全说明

- 本工具会读取本地文件系统，因此 Web 端默认只监听 `127.0.0.1`，**不要暴露到公网**；
- 所有写文件操作默认关闭（需显式 `--write`），写入前自动备份 `.bak`，内容无变化时跳过写入（幂等）；
- API Key 等敏感信息请通过环境变量提供，`.env` 与 `lc-workshop.properties` 已在 `.gitignore` 中。

---

## License

MIT
