# PolarCode

一个成熟的 Java Agent CLI 产品，对标 Claude Code。

## 测试策略

日常开发不需要每次都跑全量测试。`mvn clean package` 默认跳过测试，优先产出可手工验收的 jar；需要回归时按改动范围选择：

```bash

# 常规快速回归，跳过外部进程 / 网络超时 / 命令超时类慢测试
mvn test -Pquick

# 代码搜索 deterministic golden set
mvn test -Dtest=CodeSearchGoldenSetTest -DskipTests=false

# 发版或大范围重构前再跑全量
mvn test -DskipTests=false
```

## 启动界面

### 当前启动界面

当前启动输出以命令行实际产物为准：

```   ____   ___  _        _    ____
  |  _ \ / _ \| |      / \  |  _ \   PolarCode v16.1.0
  | |_) | | | | |     / _ \ | |_) |  Model deepseek-v4-flash (deepseek)
  |  __/| |_| | |___ / ___ \|  _ <   MCP 1/1 · 29 tools · 1/1 skills · ReAct
  |_|    \___/|_____/_/   \_\_| \_\  ReAct · Plan · MCP · Browser · Image · Tools · Memory · RAG


Tips for getting started:
1. Type / for commands and Tab completion
2. Ask coding questions, edit code or run commands
3. Attach context with @path or @image:
```

## 功能

###

- 🤖 基于 DeepSeek-v4-flash 的智能对话
- 🔄 ReAct Agent 循环（思考-行动-观察）
- 🛠️ 工具调用（文件操作、确定性代码搜索、Shell命令、项目创建、RAG 语义检索、联网搜索、MCP 动态工具）
- 💬 交互式命令行界面
- 📝 普通任务和斜杠命令提交后会先把本轮原始输入以 `>` 暗色整行块写回 transcript；输入态仍显示 `* `，单行提交只占一行，不额外追加空白行。普通任务随后再进入 Thinking / 工具调用，避免 dock 刷新或 activity 重绘后用户输入从可见历史里消失
- 🧠 默认通过流式接口获取模型输出；inline ReAct 用固定高度 live thinking 区动态预览 reasoning，content / tool call 开始前清掉 live 区并把完整 reasoning 引用块落到 transcript，回答正文用低调标记起始；web_search / web_fetch 会在折叠头展示 query / URL，并在执行后输出一行结果摘要
- 🖥️ 终端会对常见 Markdown（标题、列表、表格、代码块）做渲染后再显示；表格会按当前窗口宽度分配列宽，并在单元格内部换行，避免长 URL / 中文内容把列打散

###

- 📋 Plan-and-Execute + DAG 任务拆解与顺序执行
- ⌨️ `/plan` 一次性进入计划执行
- 🧭 更清晰的复杂任务执行顺序与依赖展示
- ⚖️ 简单任务会自动生成最小计划，不再为了凑步数扩展无关步骤

###

- 🧠 短期记忆、长期记忆与相关记忆检索
- 📦 长对话摘要压缩与 Token 预算管理
- 🧮 长上下文动态预算、prompt cache 可见化与成本估算
- 💾 `/memory` 与 `/save` 记忆管理入口

###

- 🔍 代码库实时搜索 + RAG 语义辅助（精确定位优先 glob/grep/read，自然语言模糊查询再 search_code）
- 🕸️ 代码关系图谱（类继承、接口实现、方法调用）
- 📡 本地 Ollama Embedding + 远程 API 可配置
- 🗃️ SQLite 向量存储与持久化

###

- 👥 多 Agent 协作（规划者 + 执行者 + 检查者）
- 🎯 主从架构编排器自动分配任务
- 🔍 检查者审查质量，未通过自动重试
- 🛠️ 执行者共享工具集，支持文件操作与代码检索

###

- 🔒 危险操作静态规则识别（`write_file` / `execute_command` / `create_project` / `revert_turn`）
- ⚠️ 三级危险等级展示（高危 / 中危 / 安全）
- ✅ 审批决策：批准、全部放行、拒绝、跳过、修改参数后执行
- 🔓 HITL 默认关闭，`/hitl on` 启用、`/hitl off` 关闭


## 快速开始

### 1. 配置 API Key

复制 `.env.example` 为 `.env`，并填入你的 GLM、DeepSeek、StepFun、Kimi、FreeLLMAPI 或 Agnes API Key：

```bash
cp .env.example .env
# 编辑 .env 文件，填入你的 API Key
```

或者在环境变量中设置：

```bash
export GLM_API_KEY=your_api_key_here
# 或
export STEP_API_KEY=your_step_api_key_here
export STEP_MODEL=step-3.5-flash
# 或
export KIMI_API_KEY=your_kimi_api_key_here
export KIMI_MODEL=kimi-k2.6
# 或
export FREELLMAPI_API_KEY=your_freellmapi_unified_key_here
export FREELLMAPI_BASE_URL=http://localhost:5173/v1
export FREELLMAPI_MODEL=auto
# 或
export AGNES_API_KEY=your_agnes_api_key_here
export AGNES_MODEL=agnes-2.0-flash
export AGNES_BASE_URL=https://apihub.agnes-ai.com/v1
```

也可以在 PolarCode 内用命令写入 `~/.polarcode/config.json`，不会覆盖 Kimi 配置：

```text
/config provider freellmapi --base-url http://localhost:5173/v1 --api-key <key> --model auto
/model freellmapi
/config provider agnes --api-key <key> --model agnes-2.0-flash --default
/model agnes
```

长期记忆默认保存在用户目录下的 `~/.polarcode/memory/long_term_memory.json`。
长期记忆只保存显式保存意图下的稳定事实：`/save <事实>`，或用户在自然语言里明确说“记一下 / 记住 / 以后记得”时由 Agent 调用 `save_memory`。默认保存为当前项目作用域；跨项目通用偏好可用 `/save --global <事实>` 或 `save_memory(scope=global)`。它不应包含一次性任务请求或临时文件名/目录名。
可用 `/memory list` 查看长期记忆，`/memory search <关键词>` 搜索当前项目可见记忆，`/memory delete <id>` 删除单条记忆。

项目级记忆使用 Markdown 文件维护，和 `/save` 的长期记忆分工不同：

- `~/.polarcode/PAI.md`：用户级稳定偏好，所有项目可见。
- `PAI.md` / `.polarcode/PAI.md`：项目级团队规则，建议提交到 git。
- `PAI.local.md` / `.polarcode/PAI.local.md`：本地覆盖，适合个人调试约定，建议加入 `.gitignore`。
- `@relative/path.md`：在 `PAI.md` 中导入项目根内的相对文件；越靠后的文件越接近本地覆盖，优先级越高。

可用 `/init` 为当前项目生成一份短 `PAI.md`。该命令默认不覆盖已有文件；确认需要重建时使用 `/init --force`。
代码索引默认保存在 `~/.polarcode/rag/codebase.db`。
调试日志默认滚动写入 `~/.polarcode/logs/polarcode.log`，旧日志会按保留天数和总容量自动清理。
ReAct / Plan task / SubAgent / Planner 的模型 `reasoning_content` 会以 `LLM reasoning [...]` 形式写入该日志，便于排查模型为什么选择某个工具或路径。

如果你想为某次运行指定单独目录，可以额外传入：

```bash
# 指定记忆目录
java -Dpolarcode.memory.dir=/tmp/polarcode-memory -jar target/polarcode-1.0-SNAPSHOT.jar

# 指定 RAG 索引目录
java -Dpolarcode.rag.dir=/tmp/polarcode-rag -jar target/polarcode-1.0-SNAPSHOT.jar

# 指定日志目录与保留策略
java -Dpolarcode.log.dir=/tmp/polarcode-logs \
     -Dpolarcode.log.level=DEBUG \
     -Dpolarcode.log.maxHistory=3 \
     -Dpolarcode.log.maxFileSize=5MB \
     -Dpolarcode.log.totalSizeCap=20MB \
     -jar target/polarcode-1.0-SNAPSHOT.jar
```

也可以放到 `.env` 或环境变量中：

```bash
POLARCODE_LOG_LEVEL=DEBUG
POLARCODE_LOG_DIR=/Users/yourname/.polarcode/logs
POLARCODE_LOG_MAX_HISTORY=7
POLARCODE_LOG_MAX_FILE_SIZE=10MB
POLARCODE_LOG_TOTAL_SIZE_CAP=100MB
```

### 2. 可选：配置 MCP server

MCP 子系统默认开启。`~/.polarcode/mcp.json` 不存在时，PolarCode 会自动创建默认 chrome-devtools 配置：

```json
{
  "mcpServers": {
    "chrome-devtools": {
      "command": "npx",
      "args": ["-y", "chrome-devtools-mcp@latest", "--isolated=true"]
    }
  }
}
```

需要继续接入其他 server 时，可编辑 `~/.polarcode/mcp.json` 或项目内 `.polarcode/mcp.json`：

```json
{
  "mcpServers": {
    "fetch": {
      "command": "uvx",
      "args": ["mcp-server-fetch"]
    },
    "git": {
      "command": "uvx",
      "args": ["mcp-server-git", "--repository", "${PROJECT_DIR}"]
    },
    "remote-demo": {
      "url": "https://mcp.example.com/v1",
      "headers": {"Authorization": "Bearer ${REMOTE_TOKEN}"}
    },
    "step_search": {
      "url": "https://api.stepfun.com/step_plan/v1/mcp/web_search/mcp",
      "headers": {"Authorization": "Bearer ${STEP_API_KEY}"}
    }
  }
}
```

`command` 表示 stdio server，`url` 表示 Streamable HTTP server。`${PROJECT_DIR}` / `${HOME}` 是内置变量，其他 `${VAR}` 从环境变量读取；缺失会在启动时直接提示。

`step_search` 是约定名称：如果项目 `.env`、用户 `~/.env` 或系统环境变量里存在 `STEP_API_KEY`，PolarCode 会自动内置这个远程 MCP；上面的手写配置只用于覆盖默认地址或自定义鉴权。当前模型为 `step-3.7-flash*` 时，内置 `web_search` / `web_fetch` 会优先代理到该 MCP server。

需要复用当前登录态时，Chrome 144+ 推荐打开 `chrome://inspect/#remote-debugging` 并勾选 `Allow remote debugging for this browser instance`。旧版本或需要显式 CDP 端口时，可以启动带远程调试端口和独立 user-data-dir 的 Chrome，并在这个调试 Chrome 中完成登录：

```bash
# macOS
open -na "Google Chrome" --args --remote-debugging-port=9222 --user-data-dir=/tmp/polarcode-chrome-profile

# Windows
start chrome.exe --remote-debugging-port=9222 --user-data-dir=%TEMP%\polarcode-chrome-profile

# Linux
google-chrome --remote-debugging-port=9222 --user-data-dir=/tmp/polarcode-chrome-profile
```

通常不需要用户预先切换；Agent 如果遇到登录页会自己调用 `browser_connect`。手工调试时也可以在 PolarCode 内执行：

```text
/browser status
/browser connect
/browser tabs
/browser disconnect
```

`/browser connect` 只在当前进程内把 `chrome-devtools` 切到 shared 模式，不会改写 `~/.polarcode/mcp.json`。如果希望启动后默认 shared，可手动把 args 改为：

```json
["-y", "chrome-devtools-mcp@latest", "--autoConnect"]
```

旧式 CDP HTTP JSON 端口也可使用：

```json
["-y", "chrome-devtools-mcp@latest", "--browser-url=http://127.0.0.1:9222"]
```

浏览器测试可直接让 Agent 读取动态页面，例如：

```text
帮我看下 https://mp.weixin.qq.com/s/RB7kF_BbsJZ5_Hmu9PxWdg 这篇文章讲了什么
```

期望路径是 `web_fetch` 尝试失败后，fallback 到 `mcp__chrome-devtools__navigate_page` 与 `take_snapshot`。

如果 server 支持 resources，可以直接查看或引用：

```text
/mcp resources filesystem
/mcp prompts filesystem
帮我看下 @filesystem:file://README.md 这份文档
```

OAuth 和 `sampling/createMessage` 当前未实现；远程 server 需要鉴权时仍使用 `headers` + 环境变量注入 Bearer token。

### 3. 编译运行

```bash
# 编译（默认跳过测试）
mvn clean package

# 运行（需要本地 Ollama 已启动且拉取了 nomic-embed-text；grep_code 会优先使用本机 ripgrep，未安装时自动回退）
java -jar target/polarcode-1.0-SNAPSHOT.jar
```

或者直接运行：

```bash
mvn clean compile exec:java -Dexec.mainClass="com.polarcode.cli.Main"
```

### 4. 如何进入 Plan 模式

当前默认模式是 `ReAct`。进入 `Plan-and-Execute` 的方式只有 `/plan`：

1. 输入 `/plan`
2. 下一条任务会用计划模式执行
3. 执行完成后自动回到默认 `ReAct`

如果想一条命令切模式并执行任务，可以直接输入：

```text
/plan 创建一个 demo 项目，然后读取 pom.xml，最后验证项目结构
```

这条命令执行完成后，会自动回到默认的 `ReAct` 模式。

计划生成后，CLI 会先停下来等待确认：

- 按 `Enter`：按当前计划执行
- 按 `Ctrl+O`：展开完整计划
- 按 `ESC`：折叠完整计划或取消本次计划
- 按 `I`：输入补充要求并重新规划
- 按方向键不会触发取消；只有单独按下 `ESC` 才会取消待执行 plan

## 可用工具

- `read_file` - 读取文件内容
- `write_file` - 写入文件内容
- `list_dir` - 列出目录内容
- `glob_files` - 按文件名 glob 实时查找项目内文件（只读，自动跳过常见构建/依赖目录）
- `grep_code` - 按关键字或正则实时搜索项目内代码，优先使用 ripgrep，返回文件、行号、可选上下文、partial 状态与 suggested_reads
- `execute_command` - 在当前项目目录执行短时 Shell 命令（默认 60 秒超时，黑名单拦截破坏性命令）
- `create_project` - 创建项目结构（java/python/node）
- `search_code` - 语义检索代码库（自然语言查询，适合作为模糊语义或常规搜索无果时的辅助）
- `web_search` - 搜索互联网获取实时信息
- `web_fetch` - 抓取已知 URL 并提取正文 Markdown
- `revert_turn` - 恢复到最近第 N 个 pre-turn 快照（走 HITL 与审计）
- `mcp__{server}__{tool}` - MCP server 动态提供的外部工具
- `mcp__{server}__list_resources` / `mcp__{server}__read_resource` - 支持 resources 的 MCP server 自动注册的虚拟工具

同一轮模型返回多个工具调用时，PolarCode 会并行执行这些工具；如果工具之间有依赖关系，模型应分多轮调用。

文件类与代码检索工具（`read_file` / `write_file` / `list_dir` / `glob_files` / `grep_code` / `create_project`）路径强制限定在项目根之内，越界请求会被策略层拒绝；`execute_command` 通过命令黑名单拦截 `sudo` / `rm -rf 全盘` / `mkfs` / `dd of=/dev` / fork bomb / `curl|sh` 等。`revert_turn` 会批量回写工作区，默认触发 HITL 和审计。所有 `mcp__` 前缀工具默认触发 HITL 和审计。详见 `/policy`。

## 命令

交互式斜杠命令：

- `/plan` - 下一条任务使用 Plan-and-Execute 模式
- `/plan <任务>` - 直接用 Plan-and-Execute 模式执行这条任务
- `/team` - 下一条任务使用 Multi-Agent 协作模式
- `/team <任务>` - 直接用 Multi-Agent 协作模式执行这条任务
- `/cancel` - 运行中请求取消当前任务；空闲时会提示当前没有正在运行的任务
- `/hitl on` - 启用危险操作人工审批（HITL）
- `/hitl off` - 关闭 HITL 审批
- `/hitl` - 查看 HITL 当前状态
- `/mcp` - 查看所有 MCP server 状态
- `/mcp restart <name>` - 重启单个 MCP server
- `/mcp logs <name>` - 查看 MCP server 最近 200 行 stderr 日志
- `/mcp disable <name>` - 运行时禁用 MCP server 并移除其工具
- `/mcp enable <name>` - 运行时启用 MCP server
- `/mcp resources <name>` - 查看 MCP server 暴露的 resources
- `/mcp prompts <name>` - 查看 MCP server 暴露的 prompts（只查看，不注入对话）
- `/policy` - 查看安全策略状态（路径围栏 / 命令黑名单 / 资源上限 / 审计目录）
- `/audit [N]` - 查看今日最近 N 条危险工具审计记录（默认 10）
- `/snapshot` - 查看最近 Side-Git 快照
- `/snapshot status` - 查看 Side-Git 快照状态
- `/snapshot clean` - 清理当前项目 Side-Git 快照目录
- `/restore <N>` - 恢复到最近第 N 个 pre-turn 快照
- `/memory` / `/mem` - 查看记忆系统状态
- `/memory list` - 查看长期记忆列表
- `/memory search <关键词>` - 搜索当前项目可见长期记忆
- `/memory delete <id>` - 删除单条长期记忆
- `/memory clear` - 清空长期记忆
- `/save <事实>` - 手动保存项目级关键事实到长期记忆；`/save --global <事实>` 保存跨项目通用偏好
- `save_memory` - Agent 内置工具，仅在用户明确要求保存长期偏好或稳定事实时调用；默认 `scope=project`，跨项目通用偏好才用 `scope=global`
- `/init` - 生成精简项目级记忆 `PAI.md`；已存在时不覆盖，`/init --force` 可重写
- `/export` - 导出当前 ReAct 会话对话记录为 Markdown（包含完整 system prompt），写入 `~/.polarcode/exports/session-*.md`
- `/index [路径]` - 索引代码库（默认当前目录）
- `/search <查询>` - 语义检索代码（RAG 辅助路径）
- `/graph <类名>` - 查看代码关系图谱
- `/clear` - 清空当前对话历史、短期记忆、待注入 Skill 上下文和上一轮检索记忆注入；长期记忆保留
- `/exit` / `/quit` - 退出程序

## 项目结构

```
src/main/java/com/polarcode
├── agent/
│   ├── Agent.java              # ReAct Agent
│   ├── PlanExecuteAgent.java   # Plan-and-Execute Agent
│   ├── AgentRole.java          # Agent 角色枚举
│   ├── AgentMessage.java       # Agent 间通信消息
│   ├── SubAgent.java           # 可配置子代理
│   └── AgentOrchestrator.java  # Multi-Agent 编排器
├── cli/
│   ├── Main.java               # CLI 入口
│   ├── CliCommandParser.java   # 命令解析
│   └── PlanReviewInputParser.java  # 计划审核输入
├── llm/
│   ├── GLMClient.java          # GLM API 客户端；glm-5.1 走 Coding endpoint，glm-5v-turbo 走多模态 endpoint
│   ├── DeepSeekClient.java     # DeepSeek API 客户端
│   ├── StepClient.java         # 阶跃星辰 StepFun API 客户端
│   ├── KimiClient.java         # Kimi / Moonshot API 客户端
│   ├── FreeLlmApiClient.java   # 本地 FreeLLMAPI OpenAI-compatible 网关客户端
│   └── AgnesClient.java        # Agnes AI OpenAI-compatible 客户端
├── context/
│   ├── ContextMode.java        # short / balanced / long 模式
│   ├── ContextProfile.java     # 模型窗口与上下文策略
│   └── TokenUsageFormatter.java # Token / cache / 成本展示
├── memory/
│   ├── MemoryEntry.java        # 记忆条目
│   ├── ConversationMemory.java # 短期记忆
│   ├── LongTermMemory.java     # 长期记忆
│   ├── ContextCompressor.java  # 上下文压缩
│   ├── TokenBudget.java        # Token 预算管理
│   ├── MemoryRetriever.java    # 记忆检索
│   └── MemoryManager.java      # 记忆管理类
├── plan/
│   ├── Task.java               # 任务定义
│   ├── ExecutionPlan.java      # 执行计划
│   └── Planner.java            # 规划器
├── rag/
│   ├── EmbeddingClient.java    # Embedding API 客户端
│   ├── VectorStore.java        # SQLite 向量存储
│   ├── CodeChunk.java          # 代码块模型
│   ├── CodeChunker.java        # 代码分块器
│   ├── CodeAnalyzer.java       # AST 关系分析
│   ├── CodeRelation.java       # 代码关系模型
│   ├── CodeIndex.java          # 索引管理器
│   └── CodeRetriever.java      # 检索入口
└── tool/
    └── ToolRegistry.java       # 工具注册表
```
