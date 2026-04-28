# Claw Code 上下文设计实现计划

> 更新时间：2026-04-02
> 版本：v7.1
> 状态：**执行中**

---

## 一、背景

基于 Claw Code (Rusty Claude CLI) 源码分析，将上下文设计迁移到现有项目：

1. **不使用内置 SummarizationHook** - 自己实现压缩逻辑
2. **复用现有数据库** - ChatSession/ChatMessage 持久化
3. **使用 RunnableConfig.context()** - 运行时状态（token 累积、压缩版本等）
4. **动态边界必需** - `__SYSTEM_PROMPT_DYNAMIC_BOUNDARY__` 运行时注入
5. **SupervisorAgent 集成** - 确保上下文设计在多 Agent 场景下生效

---

## 二、核心设计

### 2.1 消息类型（使用 Spring AI Alibaba 框架内置）

| Claw Code | Spring AI Alibaba | 说明 |
|-----------|-------------------|------|
| MessageRole | `Message.getMessageType()` | 系统自动管理 |
| ContentBlock.Text | `UserMessage`, `AssistantMessage` | 文本内容 |
| ContentBlock.ToolUse | `AssistantMessage.ToolCall` | 工具调用 |
| ContentBlock.ToolResult | `ToolResponseMessage.ToolResponse` | 工具结果 |
| Token 统计 | `ChatResponseMetadata.getUsage()` | 自动返回 |

### 2.2 压缩配置（与 Claw Code 一致）

```yaml
preserveRecentMessages: 4    # 保留最近 4 条消息
maxEstimatedTokens: 10000    # 超过 10K tokens 时压缩
```

### 2.3 压缩摘要格式（与 Claw Code 一致）

```xml
<summary>
Conversation summary:
- Scope: 20 earlier messages compacted (user=10, assistant=8, tool=2).
- Tools mentioned: bash, read_file, write_file.
- Recent user requests:
  - Please implement the login feature
  - Add user authentication
- Pending work:
  - Add unit tests for auth module
- Key files referenced: src/auth/login.rs, src/auth/session.rs
- Key timeline:
  - user: Implement login feature
  - assistant: I'll help you implement...
</summary>
```

### 2.4 动态边界

- **标记**: `__SYSTEM_PROMPT_DYNAMIC_BOUNDARY__`
- **运行时注入**: 压缩摘要 + 用户画像 + 会话摘要
- **使用方式**: 在 Agent 的 systemPrompt 中预留标记

---

## 三、任务列表

### 3.1 新增文件（v7.0 已完成）

| 任务ID | 文件 | 说明 | 状态 |
|--------|------|------|------|
| T-1 | `SessionContextKeys.java` | 常量定义（sessionId, userId, 运行时状态键） | ✅ 完成 |
| T-2 | `CompactionConfig.java` | 压缩配置（@Value 注入） | ✅ 完成 |
| T-3 | `ClawCodeSession.java` | 会话封装（record） | ✅ 完成 |
| T-4 | `ClawCodeSessionCompactor.java` | 压缩逻辑（含 Claw Code 摘要格式） | ✅ 完成 |
| T-5 | `ClawCodeCompressionHook.java` | 压缩 Hook（BEFORE_MODEL） | ✅ 完成 |
| T-6 | `DynamicBoundaryHook.java` | 动态边界 Hook（BEFORE_MODEL） | ✅ 完成 |

### 3.2 配置更新（v7.0 已完成）

| 任务ID | 文件 | 说明 | 状态 |
|--------|------|------|------|
| T-7 | `application.yml` | 新增压缩和动态边界配置 | ✅ 完成 |

### 3.3 代码替换（v7.0 已完成）

| 任务ID | 文件 | 说明 | 状态 |
|--------|------|------|------|
| T-8 | `FrameworkCompressionHook.java` | 标记废弃，由 ClawCodeCompressionHook 替代 | ✅ 完成 |

### 3.4 SupervisorAgent 集成（v7.1 新增）

| 任务ID | 文件 | 说明 | 状态 |
|--------|------|------|------|
| T-9 | `SupervisorAgentFramework.java` | 替换 Hook 为 ClawCodeCompressionHook + DynamicBoundaryHook | ✅ 完成 |
| T-10 | `SupervisorAgentFramework.java` | instruction 增强占位符 | ✅ 完成 |
| T-11 | `SupervisorAgentFramework.java` | 改进路由历史提取逻辑 | ✅ 完成 |
| T-12 | `SupervisorAgentFramework.java` | systemPrompt 添加动态边界标记 | ✅ 完成 |
| T-13 | `application.yml` | 启用 Hook（compression.enabled=true, dynamic-boundary.enabled=true） | ✅ 完成 |

---

## 四、文件结构

```
rect-agent-core/src/main/java/com/tlq/rectagent/
├── context/
│   ├── SessionContextKeys.java      # T-1: 常量定义
│   ├── CompactionConfig.java        # T-2: 压缩配置
│   ├── ClawCodeSession.java         # T-3: 会话封装
│   └── ClawCodeSessionCompactor.java # T-4: 压缩逻辑
├── hook/
│   ├── ClawCodeCompressionHook.java # T-5: 压缩 Hook
│   ├── DynamicBoundaryHook.java      # T-6: 动态边界 Hook
│   └── FrameworkCompressionHook.java   # T-8: 已废弃
├── agent/
│   └── SupervisorAgentFramework.java # T-9~T-12: 集成
```

---

## 五、SupervisorAgent 集成详情

### 5.1 Hook 替换

```java
// 之前
@Autowired(required = false)
private FrameworkCompressionHook compressionHook;

// 之后
@Autowired(required = false)
private ClawCodeCompressionHook clawCodeCompressionHook;

@Autowired(required = false)
private DynamicBoundaryHook dynamicBoundaryHook;
```

### 5.2 systemPrompt 增强

```java
private static final String SYSTEM_PROMPT = """
    ...
    ## 动态上下文
    __SYSTEM_PROMPT_DYNAMIC_BOUNDARY__
    ...
    """;
```

### 5.3 instruction 占位符

```java
private static final String INSTRUCTION = """
    请分析用户输入和上下文，决定调用哪个Agent：
    - 识别意图 -> intent_recognition_agent（输出：{intent_result}）
    - 数据分析 -> data_analysis_agent（输出：{analysis_result}）
    - 风险评估 -> risk_assessment_agent（输出：{risk_assessment_result}）
    - 完成 -> FINISH

    注意：
    - 如果前序Agent已有输出，请参考该输出做决策
    - 禁止连续两次调用同一Agent
    - 最多调用3次
    """;
```

### 5.4 路由历史提取

```java
private List<String> extractRouteHistory(OverAllState state) {
    String[] outputKeys = {
        "intent_result", "analysis_result", 
        "risk_assessment_result", "main_output"
    };
    // 提取所有 Agent 的 outputKey
}
```

---

## 六、配置说明

### 6.1 application.yml

```yaml
rectagent:
  compaction:
    preserve-recent: 4      # 保留最近 4 条消息
    max-tokens: 10000      # 压缩阈值
  hook:
    compression:
      enabled: true        # 启用压缩
    dynamic-boundary:
      enabled: true         # 启用动态边界
```

---

## 七、与现有代码的关系

| 现有文件 | 关系 |
|----------|------|
| `FrameworkCompressionHook.java` | 标记废弃，由 ClawCodeCompressionHook 替代 |
| `ContextInjectionMessagesHook.java` | 保留，与 ClawCodeCompressionHook 配合使用 |
| `ContextLoader.java` | 复用加载会话上下文 |
| `TokenBudgetManager.java` | 保留用于预算检查，与压缩不冲突 |
| `ChatMessage.java` / `ChatSession.java` | 复用数据库持久化 |
| `SupervisorAgentFramework.java` | 集成 ClawCodeCompressionHook + DynamicBoundaryHook |

---

## 八、验证标准

| 验证项 | 标准 |
|--------|------|
| 压缩触发 | 超过 10K tokens 时自动压缩 |
| 压缩保留 | 保留最近 4 条消息 |
| 摘要格式 | 与 Claw Code 一致（统计、工具、请求、待办、文件、时间线） |
| 动态边界 | 在 DYNAMIC_BOUNDARY 位置正确注入动态内容 |
| Hook 集成 | 正确注册到 SupervisorAgent 的子 Agent |
| 占位符 | instruction 支持 {outputKey} 引用前序输出 |
| 路由历史 | 正确追踪各 Agent 的 outputKey |

---

## 九、相关文档

- [Claw Code 源码解读](../claw_code.md)
- [Spring AI Alibaba Multi-agent 文档](https://java2ai.com/docs/frameworks/agent-framework/advanced/multi-agent)
- [Spring AI Alibaba Messages 文档](https://java2ai.com/docs/frameworks/agent-framework/tutorials/messages)
- [Spring AI Alibaba Hooks 文档](https://java2ai.com/docs/frameworks/agent-framework/tutorials/hooks)