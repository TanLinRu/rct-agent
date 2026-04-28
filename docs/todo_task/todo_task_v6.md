# SupervisorAgent 框架实现计划

> 更新时间：2026-03-29
> 版本：v6.0
> 状态：**执行中**

---

## 一、背景

根据官方文档 [SupervisorAgent](https://java2ai.com/docs/frameworks/agent-framework/advanced/multi-agent#监督者supervisoragent) 和 [Hooks](https://java2ai.com/docs/frameworks/agent-framework/tutorials/hooks) 分析，需要完成两项核心任务：

1. **SupervisorAgent 框架化** - 已完成
2. **Hook 框架化** - 待执行

---

## 二、已完成 ✅

### 2.1 SupervisorAgentFramework 实现

| 任务ID | 任务说明 | 状态 |
|--------|----------|------|
| T-1 | 创建 SupervisorAgentFramework.java | ✅ 已完成 |
| T-2 | 修复 MockChatModel 返回格式 | ✅ 已完成 |
| T-3 | 创建测试用例 | ✅ 已完成 |
| T-4 | 验证多步骤循环路由 | ✅ 已完成 |
| T-5 | 验证占位符替换 | ✅ 已完成 |

### 2.2 已创建文件

```
rect-agent-core/src/main/java/com/tlq/rectagent/agent/
├── SupervisorAgent.java              # 旧版本（自定义实现）
├── SupervisorAgentLegacy.java       # 旧版本备份
└── SupervisorAgentFramework.java   # 新版本（框架实现）✅

rect-agent-core/src/test/java/com/tlq/rectagent/agent/
└── SupervisorAgentFrameworkTest.java # 测试 ✅
```

### 2.3 核心实现（已完成）

```java
// 使用框架类
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;

supervisor = SupervisorAgent.builder()
    .name("supervisor")
    .model(chatModel)
    .systemPrompt(SYSTEM_PROMPT)
    .instruction(INSTRUCTION)
    .subAgents(Arrays.asList(intentAgent, analysisAgent, riskAgent))
    .build();
```

---

## 三、待执行 - Hook 框架化

### 3.1 当前问题

| 问题 | 说明 |
|------|------|
| 未实现框架接口 | 当前 Hook 未实现 `com.alibaba.cloud.ai.graph.agent.hook.Hook` |
| 使用自定义实现 | IncrementalCompressionHook 是自定义类，非框架 Hook |
| 上下文注入方式 | 使用自定义 ContextInjectionHook，非框架方式 |

### 3.2 官方 Hook 架构

根据 [Hooks 官方文档](https://java2ai.com/docs/frameworks/agent-framework/tutorials/hooks)，框架提供以下 Hook 类型：

```
com.alibaba.cloud.ai.graph.agent.hook
├── Hook                     # 基础接口
├── BaseHook                 # 基础实现类
├── MessagesModelHook        # 消息拦截（在发送给模型前）
├── ModelResponseHook        # 模型响应拦截
├── AgentStateHook           # Agent状态变更
├── NodeExecutionHook        # 节点执行
├── BeforeOrAfterAgentHook   # Agent执行前后
└── CustomHook              # 自定义Hook
```

### 3.3 框架内置 Hook

| Hook 类 | 功能 | 建议 |
|---------|------|------|
| `SummarizationHook` | 消息压缩/摘要 | 替换 IncrementalCompressionHook |
| `ModelCallLimitHook` | 调用次数限制 | 限流控制 |
| `PIIDetectionHook` | PII 敏感信息检测 | 安全场景 |
| `MessagesModelHook` | 消息拦截 | 上下文注入 |

---

## 四、实施计划

### 4.1 任务拆分

| 任务ID | 任务说明 | 依赖 | 状态 |
|--------|----------|------|------|
| T-6 | 分析框架内置 Hook 接口 | - | 待开始 |
| T-7 | 创建自定义 MessagesModelHook | T-6 | 待开始 |
| T-8 | 改造 IncrementalCompressionHook 使用 SummarizationHook | T-6 | 待开始 |
| T-9 | 集成 Hooks 到 SupervisorAgent | T-7, T-8 | 待开始 |
| T-10 | 测试验证 Hook 功能 | T-9 | 待开始 |

### 4.2 实现方案

#### 4.2.1 自定义 MessagesModelHook（上下文注入）

```java
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.BaseHook;
import com.alibaba.cloud.ai.graph.agent.hook.Property;
import com.alibaba.cloud.ai.graph.agent.hook.MessagesModelHook;

public class ContextInjectionMessagesHook extends BaseHook implements MessagesModelHook {

    private final ContextLoader contextLoader;
    private final ProfileService profileService;

    @Override
    public List<Message> beforeModel(List<Message> messages, Map<String, Object> context) {
        String sessionId = (String) context.get("sessionId");
        String userId = (String) context.get("userId");

        // 加载分层上下文
        List<Message> profileMessages = contextLoader.loadProfileContext(userId);
        List<Message> sessionMessages = contextLoader.loadSessionContext(sessionId);
        List<Message> checkpointMessages = contextLoader.loadCheckpointContext(sessionId);

        // 插入到消息列表头部
        List<Message> enhanced = new ArrayList<>(messages);
        enhanced.addAll(0, profileMessages);
        enhanced.addAll(0, sessionMessages);
        enhanced.addAll(0, checkpointMessages);

        return enhanced;
    }
}
```

#### 4.2.2 增量压缩 Hook（使用框架）

```java
import com.alibaba.cloud.ai.graph.agent.hook.SummarizationHook;

public class IncrementalCompressionHook extends SummarizationHook {

    public IncrementalCompressionHook(ChatModel chatModel) {
        super(chatModel);
    }

    @Override
    protected int getThreshold() {
        return 2000; // Token 阈值
    }

    @Override
    protected String getSummarizationPrompt() {
        return "请简洁总结以下对话要点，保留关键信息...";
    }
}
```

#### 4.2.3 注册 Hooks

```java
// 在 SupervisorAgentFramework 中
List<Hook> hooks = Arrays.asList(
    new ContextInjectionMessagesHook(contextLoader, profileService),
    new IncrementalCompressionHook(chatModel),
    new ModelCallLimitHook(3)  // 最多3次调用
);

ReactAgent intentAgent = ReactAgent.builder()
    .name("intent_recognition_agent")
    .model(chatModel)
    .instruction(intentInstruction)
    .hooks(hooks)  // 注册 Hooks
    .build();
```

---

## 五、文件变更

### 5.1 新增文件

```
rect-agent-core/src/main/java/com/tlq/rectagent/hook/
├── ContextInjectionMessagesHook.java  # 新增：基于框架的消息Hook
└── FrameworkCompressionHook.java      # 新增：基于框架的压缩Hook
```

### 5.2 修改文件

```
rect-agent-core/src/main/java/com/tlq/rectagent/hook/
├── IncrementalCompressionHook.java  # 重构：继承框架Hook
├── HookConfiguration.java           # 修改：注册新Hook
└── ContextInjectionHook.java       # 标记：废弃，使用新Hook

rect-agent-core/src/main/java/com/tlq/rectagent/agent/
└── SupervisorAgentFramework.java   # 修改：集成框架Hook
```

---

## 六、验证标准

| 验证项 | 标准 | 测试方式 |
|--------|------|----------|
| Hook 接口 | 实现 `com.alibaba.cloud.ai.graph.agent.hook.Hook` | 代码审查 |
| MessagesModelHook | beforeModel 正确注入上下文 | 单元测试 |
| SummarizationHook | 消息超阈值自动压缩 | 阈值测试 |
| 集成测试 | SupervisorAgent + Hooks 正常工作 | 集成测试 |

---

## 七、相关文档

- [SupervisorAgent 官方文档](https://java2ai.com/docs/frameworks/agent-framework/advanced/multi-agent#监督者supervisoragent)
- [Hooks 官方文档](https://java2ai.com/docs/frameworks/agent-framework/tutorials/hooks)
- [Hook 接口源码](https://github.com/alibaba/spring-ai-alibaba/tree/main/spring-ai-alibaba-agent-framework/src/main/java/com/alibaba/cloud/ai/graph/agent/hook)
