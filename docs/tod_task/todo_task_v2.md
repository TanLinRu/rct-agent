# Agent 系统优化方案 v2

> 基于 `agent-log-v1.log` 全链路执行日志及源码审计，识别出 6 大核心缺陷，产出对应优化方案。

---

## 一、数据流现状总览

```
用户输入
    │
    ▼
DataGovernanceService.startNewSession()     ──► ChatSession (DB)
    │
    ▼
DataGovernanceService.recordUserMessage()   ──► ChatMessage (DB, turn_index)
    │
    ▼
ContextLoader.loadContext()                 ◄── L1热消息 / L2会话摘要 / L3用户画像
    │  ├─ loadHotLayer()      → ChatMessage.getRecentMessages(sessionId, limit=10)
    │  ├─ loadWarmLayer()     → ChatSession.summarySnapshot
    │  └─ loadProfileLayer()  → extractProfileTags(userId)  ⚠️ 空实现，返回 []
    │
    ▼
CoordinatorAgent.processRequest(userInput)
    │
    ▼
SequentialAgentExecutor.execute([Intent→Prompt→DataAnalysis], userInput)
    │
    ├─ Agent-1: IntentRecognitionAgent   ◄── 仅收到 userInput（无上下文）
    │      输出: {"intent":"...","entities":[...],"confidence":0.95}
    │
    ├─ Agent-2: DynamicPromptAgent        ◄── 仅收到 intent 输出（无用户画像/历史）
    │      systemPrompt: "请根据用户意图生成一个优化的提示词"
    │      instruction:  "用户意图：{user_intent}\n请根据用户意图生成一个优化的提示词。"  ⚠️ 模板未填充
    │      输出: "请基于以下风险项目数据..."
    │
    └─ Agent-3: DataAnalysisAgent         ◄── 仅收到 generated_prompt（无 session/context）
           systemPrompt: "你是资深数据安全分析专家..."
           instruction:  "提示词：{generated_prompt}\n请根据提示词执行数据分析任务。"  ⚠️ 模板未填充
           输出: 完整分析报告 (~4000字)
    │
    ▼
CoordinatorAgent 返回 finalOutput 字符串
    │
    ▼
测试代码手动调用 DataGovernanceService.recordAssistantMessage()  ⚠️ Agent层未集成持久化
    │
    ▼
DataGovernanceService.updateSessionSummary()  ──► ChatSession.summary_snapshot
    │
    ▼
DataGovernanceService.recordProfileChange()   ──► ProfileChange (手动注入，非自动推断)
```

---

## 二、核心缺陷清单

### 缺陷 1 — 上下文未注入 Agent 执行链路（严重）

**表现**：`ContextLoader.loadContext()` 被调用后，返回的 `Context` 对象（包含 L1/L2/L3 三层数据）**从未传递给任何 Agent**。

```java
// CoordinatorAgent.java:52-55
SequentialAgentExecutor.SequentialResult result = sequentialAgentExecutor.execute(
        agents, userInput, outputKeyMap);  // ⚠️ 只有 userInput，没有 context
```

- `IntentRecognitionAgent` 不知道用户历史、偏好、话题背景
- `DynamicPromptAgent` 不知道用户画像，无法个性化生成 Prompt
- `DataAnalysisAgent` 不知道当前 session 的上下文，独立生成报告
- 结果：三层上下文设计**完全失效**，每次请求都像"首次用户"

**影响**：用户多轮对话时 Agent 无法利用历史信息，输出缺乏连贯性。

**证据（日志 1258 行）**：第 2 轮请求 `"显示前10条风险记录"`，Intent Agent 接收的消息中包含了第 1 轮全部历史（5条消息），但这是因为 `ReactAgent` 内部 `MemorySaver` 保留了 `ToolCallingChatOptions` 中的 history，**而非系统主动注入上下文**。

---

### 缺陷 2 — Profile 自动推断缺失（严重）

**表现**：`ContextLoader.extractProfileTags()` 是空实现（直接 `return new ArrayList<>()`），用户画像只能通过测试代码手动调用 `recordProfileChange()` 注入。

```java
// ContextLoader.java:83-86
private List<String> extractProfileTags(String userId) {
    List<String> tags = new ArrayList<>();  // ⚠️ 空实现
    return tags;
}
```

**证据（日志 157 行）**：首次加载时 `L3画像加载: 0个标签` —— 即使记录了 3 条 `profile_changes`，画像标签仍为 0。

**影响**：
- 无法基于对话内容自动更新用户画像
- 画像数据（`ProfileChange` 表）存在但无法被 Agent 利用
- 需要人工维护画像，违背"自动学习"设计目标

---

### 缺陷 3 — DynamicPromptAgent instruction 模板未填充（中等）

**表现**：`DynamicPromptAgent.getAgent()` 中硬编码的 `instruction` 包含 `{user_intent}` 字符串字面量，**未被 `String.format()` 替换**：

```java
// DynamicPromptAgent.java:41
.instruction("用户意图：{user_intent}\n请根据用户意图生成一个优化的提示词。")
```

调用链：
```java
// SequentialAgentExecutor.java:69
String agentOutput = agent.call(currentInput).getText();
```
`agent.call()` 接收的是 `currentInput`（即上一步的 intent JSON），而非格式化后的指令。Agent 收到的 instruction 仍是 `{user_intent}` 模板文本。

**证据（日志 181-182 行）**：
```
消息内容: [UserMessage{content='用户意图：{user_intent}\n请根据用户意图生成一个优化的提示词。', ...},
          UserMessage{content='{"intent": "analyze_risk_data", ...}')]
```
Agent 同时收到了未填充的模板文本和 intent JSON，说明模板参数传递机制未实现。

**同样问题存在于** `DataAnalysisAgent.java:45`：
```java
.instruction("提示词：{generated_prompt}\n请根据提示词执行数据分析任务。")
```
---

### 缺陷 4 — Agent 层未集成响应持久化（中等）

**表现**：`CoordinatorAgent.processRequest()` 返回 `String` 响应，但**不调用** `DataGovernanceService.recordAssistantMessage()`。持久化完全依赖调用方手动处理。

```java
// CoordinatorAgent.java:57-66
String finalOutput = result.getFinalOutput();
// ...
return finalOutput;  // ⚠️ 没有数据持久化
```

测试代码需要手动调用：
```java
// 测试中
ChatMessage aiMsg = dataGovernanceService.recordAssistantMessage(sessionId, response, response, tokenJson);
```

**影响**：
- 生产环境使用时若调用方忘记持久化，消息丢失
- 断点恢复（Checkpoint）依赖消息持久化，无持久化则无法恢复
- 无法利用历史 AI 响应做自我改进

---

### 缺陷 5 — SequentialAgentExecutor 输出仅顺序传递（中等）

**表现**：每个 Agent 只收到前一个 Agent 的输出，不保留中间结果用于综合决策。

```java
// SequentialAgentExecutor.java:76
currentInput = agentOutput;  // 丢弃了之前的输出
```

- `IntentRecognitionAgent` 的 JSON 输出（意图/实体/置信度）被作为 Prompt 文本传给下一步
- `DynamicPromptAgent` 无法同时看到原始意图 + 分析结果来做 Prompt 优化
- 最终 `DataAnalysisAgent` 只收到"优化后的 Prompt"，不知道自己的分析结果如何

**证据（日志 179 行）**：
```
Executing agent agent-1591535578 (2/3): {"intent": "analyze_risk_data", "entities": ["risk_project"], "confidence": 0.95}
```
Agent-2 收到的是 Agent-1 的 JSON 输出字符串（而非结构化数据），且没有携带 Agent-1 的上下文。

---

### 缺陷 6 — Agent 执行结果不参与响应（低）

**表现**：`CoordinatorAgent.processRequest()` 日志显示各阶段输出为 `null`：

```java
// 日志 358 行
各阶段输出: user_intent=null, generated_prompt=null, analysis_result=null
```

原因：`SequentialResult.getData(key)` 查找的是 `AgentDataContext`（Map），key 是 `user_intent`/`generated_prompt`/`analysis_result`，但 `SequentialAgentExecutor` 中存入的 key 是 `outputKeyMap` 映射后的值（`user_intent`/`generated_prompt`/`analysis_result`），而存入的 value 是 `agentOutput` 字符串。

```java
// SequentialAgentExecutor.java:64
String outputKey = outputKeyMap.getOrDefault(agentName, agentName);  // e.g. "user_intent"
// ...
context.put(outputKey, agentOutput);  // 应该能取到
```

理论上应该非 null，但日志显示 null，说明 `result.getData("user_intent")` 返回了 null。检查 `AgentDataContext` 实现确认查找逻辑正确，怀疑是日志打印时机早于数据写入或值确实未成功存储。

日志级别为 `DEBUG` 且应用了 `{NULL}` 占位符处理，说明实际值确实为 null。

根本原因在于 `SequentialAgentExecutor.execute()` 中，`outputKeyMap` 的 key 是 agent 的注册名称（如 `intent_recognition_agent`），而 `outputKeyMap.getOrDefault(agentName, agentName)` 会根据 agent 名称查找对应的输出 key，但 Map 中可能没有这个名称的映射。

既然日志输出为 null，说明 `context.put()` 没有执行成功。

另一个问题是 `DynamicPromptAgent` 和 `DataAnalysisAgent` 的 `instruction` 模板中包含占位符如 `{user_intent}` 和 `{generated_prompt}`，但这些模板从未被解析或替换，导致 Agent 收到的 prompt 仍然包含原始占位符文本。

---

## 三、优化方案

### 方案 1 — 上下文注入到 Agent 链路（优先级：P0）

**目标**：让 L1/L2/L3 三层上下文贯穿整个 Agent 执行过程。

#### 1.1 扩展 CoordinatorAgent 接收 Context

```java
// CoordinatorAgent.java
public String processRequest(String userInput, Context context) {
    // ...
    Map<String, String> enrichedInput = new HashMap<>();
    enrichedInput.put("userInput", userInput);
    enrichedInput.put("contextJson", serializeContext(context));
    enrichedInput.put("profileTags", String.join(",", context.getProfileTags()));
    enrichedInput.put("sessionSummary", context.getSessionSummary() != null ? context.getSessionSummary() : "");
    
    // 传给 IntentRecognitionAgent
    String enrichedUserInput = buildEnrichedInput(enrichedInput);
    // ...
}

private String buildEnrichedInput(Map<String, String> enriched) {
    return String.format(
        "【用户上下文】\n兴趣标签: %s\n会话摘要: %s\n\n【用户输入】\n%s",
        enriched.get("profileTags"),
        enriched.get("sessionSummary"),
        enriched.get("userInput")
    );
}
```

#### 1.2 为 DynamicPromptAgent 增加结构化上下文

将意图 JSON 替换为结构化上下文对象：

```java
public String processRequest(String userInput, Context context, IntentResult intent) {
    Map<String, Object> promptContext = new HashMap<>();
    promptContext.put("userIntent", intent.getIntent());
    promptContext.put("entities", intent.getEntities());
    promptContext.put("confidence", intent.getConfidence());
    promptContext.put("profileTags", context.getProfileTags());
    promptContext.put("sessionSummary", context.getSessionSummary());
    promptContext.put("recentTopics", extractRecentTopics(context.getHotMessages()));
    
    // 传给 DynamicPromptAgent（结构化数据，Agent 自行解析）
    return objectMapper.writeValueAsString(promptContext);
}
```

#### 1.3 为 DataAnalysisAgent 增加 Session 上下文

```java
// 在 DynamicPromptAgent 生成 prompt 后，将以下信息也注入：
Map<String, Object> analysisContext = new HashMap<>();
analysisContext.put("generatedPrompt", generatedPrompt);
analysisContext.put("sessionId", sessionId);
analysisContext.put("userProfile", context.getProfileTags());
analysisContext.put("dataScope", determineDataScope(intent));  // 根据意图确定数据范围
```

---

### 方案 2 — Profile 自动推断引擎（优先级：P0）

**目标**：从对话内容中自动提取并更新用户画像，替代手动 `recordProfileChange()`。

#### 2.1 新增 ProfileInferenceService

```java
@Service
@RequiredArgsConstructor
public class ProfileInferenceService {
    
    private final ProfileChangeService profileChangeService;
    
    public void inferAndRecord(String userId, String userInput, String aiResponse) {
        List<ProfileChange> changes = new ArrayList<>();
        
        // 意图关键词匹配
        changes.addAll(inferInterestArea(userInput, aiResponse));
        
        // 复杂度推断
        changes.addAll(inferExpertiseLevel(userInput, aiResponse));
        
        // 数据范围偏好
        changes.addAll(inferDataScopePreference(userInput));
        
        // 写入变更记录
        for (ProfileChange change : changes) {
            if (isSignificantChange(change)) {
                profileChangeService.recordChange(
                    change.getUserId(),
                    change.getFieldName(),
                    change.getOldValue(),
                    change.getNewValue(),
                    change.getReasoning()
                );
            }
        }
    }
    
    private List<ProfileChange> inferInterestArea(String userInput, String aiResponse) {
        List<ProfileChange> changes = new ArrayList<>();
        String combined = (userInput + " " + aiResponse).toLowerCase();
        
        Map<String, String> keywordMap = Map.of(
            "account_security", "账户|安全|登录|密码|MFA|暴力破解",
            "device_fingerprint", "设备|指纹|FP|模拟器|多开|越狱",
            "risk_analysis", "风险|评估|RPI|概率|影响",
            "data_export", "导出|报表|下载|CSV|PDF",
            "visualization", "图表|可视化|热力图|看板"
        );
        
        for (Map.Entry<String, String> entry : keywordMap.entrySet()) {
            if (Pattern.matches(".*" + entry.getValue() + ".*", combined)) {
                changes.add(ProfileChange.builder()
                    .userId(currentUserId)
                    .fieldName("interest_area")
                    .newValue(entry.getKey())
                    .reasoning("基于对话内容自动推断")
                    .build());
            }
        }
        return changes;
    }
    
    // ... inferExpertiseLevel, inferDataScopePreference
}
```

#### 2.2 在 CoordinatorAgent 中集成自动推断

```java
public String processRequest(String userInput, Context context) {
    String response = executeAgents(userInput, context);
    
    // 自动推断并记录画像变更
    profileInferenceService.inferAndRecord(
        context.getUserId(), userInput, response);
    
    return response;
}
```

#### 2.3 实现 extractProfileTags()

```java
// 从 profile_changes 表聚合最新标签
private List<String> extractProfileTags(String userId) {
    List<ProfileChange> changes = profileChangeService.getChangesByUserId(userId);
    
    // 按字段名分组，取每个字段的最新值
    Map<String, ProfileChange> latestByField = new LinkedHashMap<>();
    for (ProfileChange change : changes) {
        latestByField.putIfAbsent(change.getFieldName(), change);
    }
    
    return latestByField.values().stream()
        .filter(c -> c.getNewValue() != null)
        .map(c -> c.getFieldName() + ":" + c.getNewValue())
        .collect(Collectors.toList());
}
```

---

### 方案 3 — Prompt 模板参数自动填充（优先级：P1）

**问题根因**：`instruction` 中的 `{placeholder}` 未被替换就直接传给了 Agent。

#### 3.1 废弃硬编码 instruction，改用动态模板

```java
public ReactAgent getAgent(String template) {
    return ReactAgent.builder()
        .name("dynamic_prompt_agent")
        // ...
        .instruction(template)  // 模板由调用方传入并已填充
        .build();
}
```

#### 3.2 CoordinatorAgent 负责模板填充

```java
// 在调用 DynamicPromptAgent 前填充模板
String filledInstruction = String.format(
    "用户意图：%s\n上下文：%s\n请生成优化的提示词。",
    intentResult.getIntent(),
    contextSummary
);
agent.getAgent(filledInstruction).call(intentResult);
```

#### 3.3 统一 Prompt 模板管理

```yaml
# application.yml
rectagent:
  prompts:
    intent-recognition: "分析以下用户输入，识别意图和实体：\n{user_input}"
    dynamic-prompt: |
      用户意图：{intent}
      置信度：{confidence}
      实体：{entities}
      用户画像：{profile_tags}
      会话摘要：{session_summary}
      请生成优化的提示词。
    data-analysis: |
      分析任务：{prompt}
      用户背景：{profile_tags}
      数据范围：{data_scope}
      请执行数据分析。
```

---

### 方案 4 — Agent 层集成响应持久化（优先级：P1）

**目标**：让 `CoordinatorAgent` 自动完成消息持久化，减轻调用方负担。

```java
public record AgentResponse(
    String content,
    String sessionId,
    int turnIndex,
    String tokenUsage
) {}

public AgentResponse processRequest(String userInput, Context context) {
    String response = executeAgents(userInput, context);
    
    // 自动持久化 AI 响应
    int turnIndex = chatMessageService.getMaxTurnIndex(context.getSessionId()) + 1;
    ChatMessage savedMsg = chatMessageService.saveMessage(
        context.getSessionId(),
        turnIndex,
        ChatMessage.Role.ASSISTANT.name(),
        response,
        response
    );
    
    return new AgentResponse(response, context.getSessionId(), turnIndex, 
        estimateTokenUsage(response));
}
```

---

### 方案 5 — 中间结果保留与综合决策（优先级：P2）

**目标**：让后续 Agent 能访问前面所有 Agent 的输出，而非只能看到前一个。

```java
public static class SequentialResult {
    private final String finalOutput;
    private final AgentDataContext dataContext;
    private final Map<String, String> agentOutputs;  // 保留所有输出
    
    // 新增：获取上下文摘要
    public String getContextSummary() {
        return String.format(
            "【意图】%s\n【Prompt】%s\n【分析结果】%s",
            agentOutputs.get("user_intent"),
            agentOutputs.get("generated_prompt"),
            agentOutputs.get("analysis_result")
        );
    }
}

// DataAnalysisAgent 现在可以访问完整上下文
Map<String, String> allOutputs = result.getAgentOutputs();
String fullContext = result.getContextSummary();
```

---

### 方案 6 — 修复 Agent 输出提取 null 问题（优先级：P1）

**问题根因**：`outputKeyMap` 的 key 与 `AgentReflectionUtil.getAgentName()` 返回值不匹配。

#### 6.1 统一 agent 名称

确保 `AgentReflectionUtil.getAgentName()` 返回的值与 `outputKeyMap` 中的 key 一致：

```java
// CoordinatorAgent.java
Map<String, String> outputKeyMap = new HashMap<>();
outputKeyMap.put("intent_recognition_agent", "user_intent");     // key = agent 名称
outputKeyMap.put("dynamic_prompt_agent", "generated_prompt");
outputKeyMap.put("data_analysis_agent", "analysis_result");
```

#### 6.2 验证 Agent 名称一致性

```java
// 在 SequentialAgentExecutor.execute() 开始处添加校验
for (ReactAgent agent : agents) {
    String name = AgentReflectionUtil.getAgentName(agent);
    if (!outputKeyMap.containsKey(name) && !outputKeyMap.containsValue(name)) {
        log.warn("Agent '{}' has no mapping in outputKeyMap", name);
    }
}
```

---

## 四、实施路线图

| 阶段 | 内容 | 优先级 | 预计工时 |
|------|------|--------|----------|
| **Phase 1** | 实现 `extractProfileTags()` 真实查询 + ProfileInferenceService 自动推断 | P0 | 4h |
| **Phase 1** | 修复 DynamicPromptAgent / DataAnalysisAgent instruction 模板填充 | P0 | 2h |
| **Phase 2** | Context 注入 CoordinatorAgent.execute()，改造为带上下文的 agent 调用 | P0 | 6h |
| **Phase 3** | Agent 层集成响应持久化 + 自动画像记录 | P1 | 3h |
| **Phase 4** | 中间结果保留（AgentOutputs）供后续 Agent 综合决策 | P2 | 4h |
| **Phase 5** | 修复 outputKeyMap 名称匹配 + 日志验证 | P1 | 1h |
| **Phase 6** | 端到端集成测试（真实 API），验证上下文链路贯通 | P0 | 3h |

**总预计工时**：~23 小时

---

## 五、验证方法

### 5.1 单元验证

```bash
# 场景一验证点：
# - L3画像加载: 应显示 N>0 个标签（不再为0）
# - DynamicPromptAgent 收到的 instruction 不含 {user_intent} 字面量
# - CoordinatorAgent 日志: 各阶段输出非 null

# 场景二验证点：
# - 画像演变: 自动推断出 interest_area: account_security → device_fingerprint（无需手动 recordProfileChange）
# - DataAnalysisAgent 输出结合用户画像做个性化分析
```

### 5.2 性能验证

```bash
# Token 消耗对比
# 优化前：每轮 Agent 调用 ~1626ms（首次）+ ~5381ms（Prompt生成）+ ~21s（分析）
# 优化后：增加上下文注入后，Prompt生成应更精准，减少迭代次数
```

### 5.3 重放日志分析

关键断言点（可直接从日志提取验证）：
1. `L3画像加载: N个标签` — N > 0
2. `各阶段输出: user_intent=...` — 非 null
3. `画像变更记录成功: N条` — N >= 3 且为自动推断
4. `消息内容` — 不含 `{user_intent}` 字面模板
