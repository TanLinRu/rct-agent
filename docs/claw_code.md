# Claw Code 源码解读文档

> 基于 Rusty Claude CLI 源码分析

---

## 一、概述

Claw Code 是 Claude Code 的 clean-room Python/Rust 重实现，其核心是一个 **Agent Runtime Harness**，负责管理 AI Agent 的完整生命周期：工具编排、上下文管理、权限控制、会话持久化。

**核心模块**：

- `session.rs` - 会话管理
- `permissions.rs` - 权限控制
- `compact.rs` - 上下文压缩
- `prompt.rs` - Prompt 构建
- `conversation.rs` - 运行时核心

---

## 二、源码文件位置索引

### 2.1 核心模块源码位置

| 模块            | 文件路径                                  | 行数 |
| --------------- | ----------------------------------------- | ---- |
| **会话管理**    | `rust/crates/runtime/src/session.rs`      | 432  |
| **权限控制**    | `rust/crates/runtime/src/permissions.rs`  | 227  |
| **上下文压缩**  | `rust/crates/runtime/src/compact.rs`      | 485  |
| **Prompt 构建** | `rust/crates/runtime/src/prompt.rs`       | 700  |
| **运行时核心**  | `rust/crates/runtime/src/conversation.rs` | 583  |
| **配置加载**    | `rust/crates/runtime/src/config.rs`       | ~500 |
| **Token 统计**  | `rust/crates/runtime/src/usage.rs`        | ~300 |

### 2.2 CLI 入口文件

| 模块       | 文件路径                                   | 行数 |
| ---------- | ------------------------------------------ | ---- |
| **主入口** | `rust/crates/rusty-claude-cli/src/main.rs` | 2907 |

### 2.3 目录结构

```
claw-code-main/
├── rust/
│   └── crates/
│       ├── runtime/
│       │   └── src/
│       │       ├── session.rs       ← 会话管理
│       │       ├── permissions.rs   ← 权限控制
│       │       ├── compact.rs       ← 上下文压缩
│       │       ├── prompt.rs        ← Prompt 构建
│       │       ├── conversation.rs  ← 运行时核心
│       │       ├── config.rs        ← 配置加载
│       │       └── usage.rs        ← Token 统计
│       │
│       └── rusty-claude-cli/
│           └── src/
│               └── main.rs          ← CLI 入口
```

---

## 三、核心数据结构

### 3.1 消息模型 (session.rs)

**源码位置**: `rust/crates/runtime/src/session.rs` 第 9-46 行

```rust
// 消息角色
pub enum MessageRole {
    System,
    User,
    Assistant,
    Tool,
}

// 内容块
pub enum ContentBlock {
    Text { text: String },
    ToolUse { id: String, name: String, input: String },
    ToolResult { tool_use_id: String, tool_name: String, output: String, is_error: bool },
}

// 会话消息
pub struct ConversationMessage {
    pub role: MessageRole,
    pub blocks: Vec<ContentBlock>,
    pub usage: Option<TokenUsage>,
}

// 会话
pub struct Session {
    pub version: u32,
    pub messages: Vec<ConversationMessage>,
}
```

### 3.2 Mock 数据示例

**JSON 结构**:

```json
{
  "version": 1,
  "messages": [
    {
      "role": "user",
      "blocks": [{"type": "text", "text": "请列出当前目录文件"}]
    },
    {
      "role": "assistant",
      "blocks": [
        {"type": "text", "text": "好的，我来列出文件"},
        {"type": "tool_use", "id": "tool-1", "name": "bash", "input": "ls -la"}
      ],
      "usage": {"input_tokens": 100, "output_tokens": 50}
    },
    {
      "role": "tool",
      "blocks": [{"type": "tool_result", "tool_use_id": "tool-1", "tool_name": "bash", "output": "total 12\n...", "is_error": false}]
    }
  ]
}
```

### 3.3 单轮对话消息流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                     单轮对话消息流程                                 │
├─────────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐                                                   │
│  │ System Msg   │  ← 系统提示（可选，配置决定）                      │
│  │ role: system │                                                   │
│  │ blocks: []   │                                                   │
│  └──────────────┘                                                   │
│           │                                                          │
│           ▼                                                          │
│  ┌──────────────┐                                                   │
│  │  User Msg    │  ← 用户输入                                       │
│  │ role: user   │                                                   │
│  │ blocks:      │                                                   │
│  │   [Text]     │                                                   │
│  └──────────────┘                                                   │
│           │                                                          │
│           ▼                                                          │
│  ┌──────────────┐                                                   │
│  │Assistant Msg│  ← AI 回复（可能包含工具调用）                     │
│  │ role: assit  │                                                   │
│  │ blocks:      │                                                   │
│  │   [Text]     │  ← AI 思考文本                                     │
│  │   [ToolUse] │  ← 调用工具意图                                    │
│  │ usage: {...} │  ← token 消耗统计                                 │
│  └──────────────┘                                                   │
│           │                                                          │
│           ▼                                                          │
│  ┌──────────────┐                                                   │
│  │  Tool Msg    │  ← 工具执行结果                                   │
│  │ role: tool   │                                                   │
│  │ blocks:      │                                                   │
│  │   [ToolResult]                                                   │
│  └──────────────┘                                                   │
│           │                                                          │
│           ▼                                                          │
│  ┌──────────────┐                                                   │
│  │Assistant Msg │  ← AI 基于工具结果最终回复                        │
│  │ role: assit  │                                                   │
│  │ blocks:      │                                                   │
│  │   [Text]     │  ← 最终答案                                       │
│  └──────────────┘                                                   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 四、权限系统 (permissions.rs)

**源码位置**: `rust/crates/runtime/src/permissions.rs`

### 4.1 权限级别定义

```rust
// 第 3-8 行：三级权限模式
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum PermissionMode {
    ReadOnly,              // 只读：只能读取文件
    WorkspaceWrite,        // 工作区写：可以修改项目文件
    DangerFullAccess,     // 危险全访问：可以执行任意命令
}
```

### 4.2 权限策略

```rust
// 第 45-49 行：权限策略
pub struct PermissionPolicy {
    active_mode: PermissionMode,                    // 当前激活的权限模式
    tool_requirements: BTreeMap<String, PermissionMode>, // 工具->权限要求映射
}
```

### 4.3 授权流程

**源码位置**: `rust/crates/runtime/src/permissions.rs` 第 84-129 行

```
Agent 调用工具 → 检查 active_mode >= required_mode
    ↓
权限足够 → 直接执行
权限不足 → 检查是否可交互升级
    ↓
WorkspaceWrite → DangerFullAccess → 询问用户 → 允许/拒绝
其他情况 → 直接拒绝
```

### 4.4 权限层级图

```
┌─────────────────────────────────────────────────────────────┐
│                  权限层级 (从低到高)                         │
├─────────────────────────────────────────────────────────────┤
│   ReadOnly (1)                                              │
│   ├── glob_search                                           │
│   ├── grep_search                                           │
│   └── read_file                                             │
│        │                                                    │
│        ▼                                                    │
│   WorkspaceWrite (2)                                        │
│   ├── write_file                                            │
│   ├── create_file                                           │
│   └── edit_file                                             │
│        │                                                    │
│        ▼                                                    │
│   DangerFullAccess (3)                                      │
│   ├── bash (shell命令)                                       │
│   ├── remove_file                                           │
│   └── network请求                                           │
└─────────────────────────────────────────────────────────────┘
```

---

## 五、上下文压缩 (compact.rs)

**源码位置**: `rust/crates/runtime/src/compact.rs`

### 5.1 压缩配置

```rust
// 第 3-16 行：压缩配置
pub struct CompactionConfig {
    pub preserve_recent_messages: usize,   // 保留最近 N 条消息
    pub max_estimated_tokens: usize,        // 最大 token 阈值
}

impl Default for CompactionConfig {
    fn default() -> Self {
        Self {
            preserve_recent_messages: 4,   // 保留最近 4 条
            max_estimated_tokens: 10_000,   // 超过 10K token 时压缩
        }
    }
}
```

### 5.2 压缩流程

**源码位置**: `rust/crates/runtime/src/compact.rs` 第 74-111 行

```
原始会话 (20条消息, 15K tokens)
           │
           ▼
┌─────────────────────┬─────────────────────┐
│  旧消息 (16条)      │  最近消息 (4条)     │
│  → 要压缩          │  → 保留            │
└─────────────────────┴─────────────────────┘
           │
           ▼
    生成结构化摘要
    - 消息统计
    - 工具列表
    - 用户请求
    - 待办工作
    - 关键文件
           │
           ▼
┌─────────────────────────────────────┐
│ messages[0] = SystemMessage(摘要)   │  ← 新增
│ messages[1..] = 保留的最近消息      │  ← 裁剪
└─────────────────────────────────────┘
```

### 5.3 摘要输出示例

压缩后的系统消息内容：

```
<summary>
Conversation summary:
- Scope: 20 earlier messages compacted (user=10, assistant=8, tool=2).
- Tools mentioned: bash, read_file, write_file.
- Recent user requests:
  - Please implement the login feature
  - Add user authentication
  - Fix the session timeout issue
- Pending work:
  - Add unit tests for auth module
- Key files referenced: src/auth/login.rs, src/auth/session.rs, config/app.yaml
- Current work: Implementing token refresh logic
- Key timeline:
  - user: Implement login feature
  - assistant: I'll help you implement the login feature...
  - tool_use bash(git status)
  - tool_result: On branch main...
  - assistant: Let me check the project structure...
</summary>
```

### 5.4 Token 估算

```rust
// 第 326-338 行：估算单条消息的 token
fn estimate_message_tokens(message: &ConversationMessage) -> usize {
    message.blocks.iter().map(|block| match block {
        ContentBlock::Text { text } => text.len() / 4 + 1,
        ContentBlock::ToolUse { name, input, .. } => (name.len() + input.len()) / 4 + 1,
        ContentBlock::ToolResult { tool_name, output, .. } => (tool_name.len() + output.len()) / 4 + 1,
    }).sum()
}
```

---

## 六、Prompt 构建 (prompt.rs)

**源码位置**: `rust/crates/runtime/src/prompt.rs`

### 6.1 SystemPromptBuilder

```rust
// 第 81-90 行：Prompt 构建器
pub struct SystemPromptBuilder {
    output_style_name: Option<String>,        // 输出风格名称
    output_style_prompt: Option<String>,      // 输出风格 Prompt
    os_name: Option<String>,                   // 操作系统名称
    os_version: Option<String>,                // 操作系统版本
    append_sections: Vec<String>,               // 追加的章节
    project_context: Option<ProjectContext>,   // 项目上下文
    config: Option<RuntimeConfig>,             // 运行时配置
}
```

### 6.2 构建流程

**源码位置**: `rust/crates/runtime/src/prompt.rs` 第 131-153 行

```rust
pub fn build(&self) -> Vec<String> {
    let mut sections = Vec::new();
    sections.push(get_simple_intro_section(self.output_style_name.is_some()));
    if let (Some(name), Some(prompt)) = (&self.output_style_name, &self.output_style_prompt) {
        sections.push(format!("# Output Style: {name}\n{prompt}"));
    }
    sections.push(get_simple_system_section());
    sections.push(get_simple_doing_tasks_section());
    sections.push(get_actions_section());
    sections.push(SYSTEM_PROMPT_DYNAMIC_BOUNDARY.to_string());
    sections.push(self.environment_section());
    // ... 更多章节
    sections
}
```

### 6.3 System Prompt 组成结构

```
┌─────────────────────────────────────────────────────────────┐
│                    System Prompt 组成结构                    │
├─────────────────────────────────────────────────────────────┤
│  1. Intro (介绍)                                              │
│  2. Output Style (可选)                                       │
│  3. System (系统说明)                                          │
│  4. Doing tasks (任务执行指南)                                 │
│  5. Actions (执行注意事项)                                     │
│  6. ★ DYNAMIC_BOUNDARY (动态边界)                              │
│  7. Environment (环境信息)                                     │
│  8. Project Context (项目上下文)                               │
│  9. Runtime Config (运行时配置)                               │
└─────────────────────────────────────────────────────────────┘
```

### 6.4 动态边界

```rust
// 第 37 行：动态边界标记
pub const SYSTEM_PROMPT_DYNAMIC_BOUNDARY: &str = "__SYSTEM_PROMPT_DYNAMIC_BOUNDARY__";
```

**动态边界工作原理**:

```
构建时：
┌─────────────────────────────────────────────────────────────┐
│ Static Part (构建时已知)                                     │
│   - Intro + System + Doing tasks + Actions                  │
│ __SYSTEM_PROMPT_DYNAMIC_BOUNDARY__                          │
│ Static Part (构建时已知)                                     │
│   - Environment + Project Context                           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
运行时：
┌─────────────────────────────────────────────────────────────┐
│ Static Part                                                  │
│ __SYSTEM_PROMPT_DYNAMIC_BOUNDARY__                          │
│ + 动态注入内容                                               │
│   - 压缩后的会话摘要 (compact.rs 生成)                       │
│   - 运行时 Hook 注入的信息                                   │
│ Static Part                                                  │
└─────────────────────────────────────────────────────────────┘
```

### 6.5 配置发现层级

**源码位置**: `rust/crates/runtime/src/prompt.rs` 第 189-220 行

```
~/.claude/settings.json        ← 用户配置
~/.claude/rules/               ← 用户规则
.project/.claude.json          ← 项目配置
.project/CLAUDE.md             ← 项目指令
.project/.claude/instructions.md
```

### 6.6 指令文件发现

```rust
fn discover_instruction_files(cwd: &Path) -> std::io::Result<Vec<ContextFile>> {
    // 搜索路径优先级：
    // 1. ./CLAUDE.md
    // 2. ./CLAUDE.local.md
    // 3. ./.claude/CLAUDE.md
    // 4. ./.claude/instructions.md
    // 5. 向上查找父目录...
}
```

---

## 七、运行时核心 (conversation.rs)

**源码位置**: `rust/crates/runtime/src/conversation.rs`

### 7.1 对话循环数据结构

```rust
// 第 89-97 行：对话运行时
pub struct ConversationRuntime<C, T> {
    session: Session,                    // 会话状态
    api_client: C,                       // API 客户端（泛型）
    tool_executor: T,                    // 工具执行器（泛型）
    permission_policy: PermissionPolicy, // 权限策略
    system_prompt: Vec<String>,          // System Prompt
    max_iterations: usize,               // 最大迭代次数
    usage_tracker: UsageTracker,        // Token 使用追踪
}
```

### 7.2 ReAct Loop 流程

**源码位置**: `rust/crates/runtime/src/conversation.rs` 第 130-218 行

```
用户输入: "帮我实现登录功能"
           │
           ▼
┌─────────────────────────────────────────────────────────────────┐
│ run_turn()                                                        │
│   1. session.messages.push(user_message)                         │
│   2. loop:                                                       │
│       ├── 构建 ApiRequest (system_prompt + messages)            │
│       ├── api_client.stream() → LLM                             │
│       ├── 解析 assistant_message                                 │
│       ├── 权限检查 + 工具执行                                     │
│       └── 循环直到无工具调用                                      │
│   3. 返回 TurnSummary                                            │
└─────────────────────────────────────────────────────────────────┘
```

### 7.3 API 请求构造

```rust
// 第 151-154 行
let request = ApiRequest {
    system_prompt: self.system_prompt.clone(),  // 静态构建的 Prompt
    messages: self.session.messages.clone(),     // Session 中的消息（含压缩摘要）
};
```

### 7.4 工具执行流程

```rust
// 第 178-209 行
for (tool_use_id, tool_name, input) in pending_tool_uses {
    // 步骤1: 权限检查
    let permission_outcome = self.permission_policy
        .authorize(&tool_name, &input, prompter.as_mut());

    // 步骤2: 根据权限结果决定
    let result_message = match permission_outcome {
        PermissionOutcome::Allow => {
            // 执行工具
            self.tool_executor.execute(&tool_name, &input)
        }
        PermissionOutcome::Deny { reason } => {
            // 拒绝执行
            ConversationMessage::tool_result(..., reason, true)
        }
    };
}
```

---

## 八、完整数据流

```
┌─────────────────────────────────────────────────────────────────────┐
│                        完整数据流                                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │ 1. 启动阶段                                                     │ │
│  │    ConfigLoader::discover() → 发现配置层级                     │ │
│  │    ProjectContext::discover() → 发现指令文件                    │ │
│  │    SystemPromptBuilder::build() → 构建 System Prompt           │ │
│  │    Session::new() 或 Session::load_from_path()                │ │
│  │    ConversationRuntime::new() → 创建运行时                     │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                              │                                      │
│                              ▼                                      │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │ 2. 对话阶段                                                     │ │
│  │    用户输入                                                     │ │
│  │       │                                                        │ │
│  │       ▼                                                        │ │
│  │    run_turn()                                                  │ │
│  │       │                                                        │ │
│  │       ├── 添加 user message                                    │ │
│  │       ├── LLM API 调用 (ReAct Loop)                           │ │
│  │       │    ├── TextDelta                                      │ │
│  │       │    ├── ToolUse                                        │ │
│  │       │    └── MessageStop                                    │ │
│  │       ├── 权限检查 (PermissionPolicy)                         │ │
│  │       ├── 工具执行 (ToolExecutor)                             │ │
│  │       └── 返回 TurnSummary                                    │ │
│  │       │                                                        │ │
│  │       ▼                                                        │ │
│  │    persist_session() → 持久化到磁盘                            │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                              │                                      │
│                              ▼                                      │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │ 3. 压缩触发（当 estimated_tokens >= 10000）                   │ │
│  │    compact_session()                                          │ │
│  │       ├── 拆分: removed + preserved                            │ │
│  │       ├── 摘要: summarize_messages()                          │ │
│  │       ├── 注入: messages[0] = SystemMessage(摘要)             │ │
│  │       └── 重建 Runtime                                        │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 九、架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Claw Code 架构                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │                    rusty-claude-cli (CLI 入口)                    │ │
│  │   - 命令行参数解析                                                │ │
│  │   - 会话管理                                                      │ │
│  │   - REPL 循环                                                    │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                    │                                    │
│                                    ▼                                    │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │                    ConversationRuntime                          │ │
│  │                                                                  │ │
│  │  ┌────────────────┐ ┌────────────────┐ ┌────────────────────┐  │ │
│  │  │   session      │ │  api_client   │ │  tool_executor    │  │ │
│  │  │  会话状态      │ │  LLM 调用     │ │  工具执行         │  │ │
│  │  └────────────────┘ └────────────────┘ └────────────────────┘  │ │
│  │                                                                  │ │
│  │  ┌────────────────┐ ┌────────────────┐ ┌────────────────────┐  │ │
│  │  │ permission_   │ │   system_     │ │  usage_tracker    │  │ │
│  │  │ policy        │ │   prompt      │ │  Token 统计       │  │ │
│  │  │ 权限控制      │ │  Prompt 构建  │ │                   │  │ │
│  │  └────────────────┘ └────────────────┘ └────────────────────┘  │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                    │                                    │
│          ┌─────────────────────────┼─────────────────────────┐          │
│          ▼                         ▼                         ▼          │
│  ┌──────────────┐        ┌──────────────┐        ┌──────────────┐      │
│  │ compact.rs   │        │ prompt.rs    │        │session.rs   │      │
│  │ 上下文压缩   │        │ Prompt 构建   │        │ 会话管理    │      │
│  └──────────────┘        └──────────────┘        └──────────────┘      │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 十、关键设计模式

| 模式         | 应用位置                           | 说明                  |
| ------------ | ---------------------------------- | --------------------- |
| **泛型**     | `ConversationRuntime<C, T>`        | 支持不同 API/工具实现 |
| **Trait**    | `ApiClient`, `ToolExecutor`        | 可插拔设计            |
| **工厂方法** | `ConversationMessage::user_text()` | 简化对象创建          |
| **策略模式** | `PermissionPolicy::authorize()`    | 可切换权限策略        |
| **动态边界** | `SYSTEM_PROMPT_DYNAMIC_BOUNDARY`   | 运行时内容注入        |

---

## 十一、会话持久化

### 11.1 保存机制

**源码位置**: `rust/crates/runtime/src/session.rs` 第 88-91 行

```rust
pub fn save_to_path(&self, path: impl AsRef<Path>) -> Result<(), SessionError> {
    fs::write(path, self.to_json().render())?;  // 序列化为 JSON 写入文件
    Ok(())
}
```

### 11.2 加载机制

**源码位置**: `rust/crates/runtime/src/session.rs` 第 93-96 行

```rust
pub fn load_from_path(path: impl AsRef<Path>) -> Result<Self, SessionError> {
    let contents = fs::read_to_string(path)?;
    Self::from_json(&JsonValue::parse(&contents)?)
}
```

### 11.3 持久化时机

| 操作                     | 是否持久化   |
| ------------------------ | ------------ |
| 每次 `run_turn()` 成功后 | ✅ 是         |
| 每次 LLM API 调用后      | ❌ 否         |
| 应用启动/恢复时          | ✅ 是（加载） |
| 手动 `/compact` 命令     | ✅ 是         |

### 11.4 数据存储位置

```
项目目录 (.claude/)                                               │
├── sessions/                                                     │
│   └── {timestamp}.json    ← 会话文件                           │
│                                                                  │
├── .claude.json               ← 项目配置                        │
│                                                                  │
└── .claude.local.json         ← 本地覆盖配置                     │
                                                                     │
用户目录 ($CLAUDE_CONFIG_HOME 或 ~/.claude/)                      │
├── settings.json             ← 用户配置                         │
└── rules/                    ← 用户规则                         │
```

---

## 十二、配置发现与加载

**源码位置**: `rust/crates/runtime/src/config.rs`

### 12.1 配置发现层级

```rust
pub fn discover(&self) -> Vec<ConfigEntry> {
    vec![
        // 用户级配置（旧位置）
        ConfigEntry { source: ConfigSource::User, path: "$HOME/.claude/settings.json" },
        
        // 用户级配置（新位置）
        ConfigEntry { source: ConfigSource::User, path: "$CLAUDE_CONFIG_HOME/settings.json" },
        
        // 项目级配置
        ConfigEntry { source: ConfigSource::Project, path: "./.claude.json" },
        
        // 本地配置
        ConfigEntry { source: ConfigSource::Local, path: "./.claude.local.json" },
    ]
}
```

### 12.2 配置优先级

```
┌─────────────────────────────────────────────────────────────────────┐
│                    配置优先级（从低到高）                            │
├─────────────────────────────────────────────────────────────────────┤
│  1. User Legacy    (~/.claude.json)          ← 最低优先级         │
│  2. User Settings ($CLAUDE_CONFIG_HOME/settings.json)             │
│  3. Project        (./.claude.json)          ← 项目级             │
│  4. Local          (./.claude.local.json)   ← 最高优先级          │
│                                                                     │
│  高优先级配置会覆盖低优先级（合并策略）                              │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 十三、关键代码位置索引

| 功能             | 文件              | 行号      |
| ---------------- | ----------------- | --------- |
| Session 序列化   | `session.rs`      | 88-96     |
| Session 反序列化 | `session.rs`      | 93-136    |
| 权限授权         | `permissions.rs`  | 84-129    |
| 压缩判断         | `compact.rs`      | 32-35     |
| 压缩执行         | `compact.rs`      | 74-111    |
| 摘要生成         | `compact.rs`      | 113-198   |
| Prompt 构建      | `prompt.rs`       | 131-153   |
| 动态边界标记     | `prompt.rs`       | 37        |
| 指令文件发现     | `prompt.rs`       | 189-220   |
| 对话循环         | `conversation.rs` | 130-218   |
| API 请求构建     | `conversation.rs` | 151-154   |
| 会话持久化       | `main.rs`         | 1140-1143 |
| 运行时构建       | `main.rs`         | 1920-1936 |
| 配置发现         | `config.rs`       | 176-204   |
| 配置加载         | `config.rs`       | 205-233   |

---

## 十四、与 LLM 交互的消息结构

```
┌─────────────────────────────────────────────────────────────────────┐
│                      LLM 最终看到的上下文                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  1. system_prompt (静态构建):                                       │
│     ┌─────────────────────────────────────────────────────────────┐ │
│     │ You are an agent...                                         │ │
│     │ __SYSTEM_PROMPT_DYNAMIC_BOUNDARY__                          │ │
│     │ # Environment context...                                    │ │
│     └─────────────────────────────────────────────────────────────┘ │
│                              │                                      │
│                              ▼                                      │
│  2. messages (Session 中的消息):                                   │
│     ┌─────────────────────────────────────────────────────────────┐ │
│     │ [0] system: "Session continued... Summary: ..."            │ │
│     │ [1] user: "继续之前的工作..."                               │ │
│     │ [2] assistant: "现在我们继续..."                            │ │
│     └─────────────────────────────────────────────────────────────┘ │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

*文档基于 Claw Code (Rusty Claude CLI) 源码分析生成*
*源码位置: D:\project\ai\claw-code-main\rust\crates\runtime\src\*