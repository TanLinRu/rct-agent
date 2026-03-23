# Rect Agent - 多模型智能体路由系统

> **版本**: 0.0.1-SNAPSHOT
> **更新**: 2026-03-23

## 项目简介

Rect Agent 是一个基于 Spring AI Alibaba 的多模型智能体路由系统，支持 DashScope、OpenAI、Anthropic 三大 LLM 提供商，通过可插拔的路由策略自动选择最优模型执行任务。

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.3.5 |
| Java 版本 | Java 21 |
| AI 框架 | Spring AI Alibaba Agent Framework 1.1.2.0 |
| 构建工具 | Maven 3.6+ |
| 测试框架 | JUnit 4 + HttpServer (嵌入式) |

## 核心架构

```
┌──────────────────────────────────────────────────────────────┐
│                        CoordinatorAgent                       │
│     顺序编排：意图识别 → 动态提示词 → 数据分析                  │
│     使用框架 SequentialAgent，按顺序执行 3 个子 Agent          │
└─────────────────────────┬────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          │               │               │
          ▼               ▼               ▼
    ┌────────────┐  ┌────────────┐  ┌────────────┐
    │  Intent    │  │  Dynamic   │  │   Data     │
    │Recognition │  │   Prompt   │  │  Analysis  │
    │   Agent    │  │   Agent    │  │   Agent    │
    │            │  │            │  │            │
    │ outputKey: │  │ outputKey: │  │ outputKey: │
    │user_intent │─▶│generated_  │─▶│analysis_   │
    │            │  │  prompt    │  │  result    │
    └────────────┘  └────────────┘  └────────────┘
          │               │               │
          └───────────────┼───────────────┘
                          │ Hook 链
                          ▼
         ┌───────────────────────────────────────┐
         │           Hook / Interceptor           │
         │                                        │
         │  BEFORE_MODEL:                        │
         │    • ContextInjectionHook (上下文注入)  │
         │    • SummarizationHook (Token 压缩)    │
         │                                        │
         │  AFTER_MODEL:                         │
         │    • ProfileInferenceHook (画像推断)    │
         │    • ModelCallLimitHook (限流)         │
         │                                        │
         │  Interceptors:                         │
         │    • ModelProcessInterceptor (日志)    │
         │    • ToolMonitoringInterceptor (监控)  │
         └───────────────────┬───────────────────┘
                             │
                             ▼
         ┌───────────────────────────────────────┐
         │              ModelRouter               │  根据策略选择最优 Provider
         └───────────────────┬───────────────────┘
                             │
            ┌────────────────┼────────────────┐
            │                │                │
            ▼                ▼                ▼
      ┌─────────┐      ┌─────────┐      ┌──────────┐
      │DashScope│      │ OpenAI  │      │Anthropic │
      │(阿里云) │      │ (官方)  │      │ (Anthropic)│
      └─────────┘      └─────────┘      └──────────┘
```

## 核心设计流程

### 流程一：SupervisorAgent 编排流程

```
用户输入
   │
   ▼
┌─────────────────────────────────────────┐
│  SupervisorAgent.invoke(input)           │
│                                         │
│  for (AgentTool tool : tools)          │
│      acc = tool.apply(acc)             │  ← 依次执行工具链
│                                         │
│  return modelRouter.route(acc)         │  ← 路由到最优模型
└─────────────────────────────────────────┘
   │
   ▼
模型响应
```

### 流程二：ModelRouter 路由决策流程

```
input
  │
  ▼
┌──────────────────────────────────────┐
│ ModelRegistry.getAll()               │  ← 获取所有已注册的 Provider
└────────────────┬───────────────────┘
                 │
                 ▼
┌──────────────────────────────────────┐
│ RoutingStrategy.select(providers, ctx) │  ← 根据策略选择最优 Provider
└────────────────┬───────────────────┘
                 │
    ┌────────────┼────────────┐
    │            │            │
    ▼            ▼            ▼
 CostBased  PriorityBased  Capability...
 (最低成本)   (最高优先级)   (能力匹配)
    │            │            │
    └────────────┴────────────┘
                 │ 选中 Provider
                 ▼
┌──────────────────────────────────────┐
│ Provider.call(input)                  │  ← 调用模型
│  → DashScopeProvider                 │
│  → OpenAIProvider                    │
│  → AnthropicProvider                 │
└────────────────┬────────────────────┘
                 │
                 ▼
              响应结果
```

### 流程三：Provider HTTP 调用流程（以 OpenAI 为例）

```
Provider.call(input)
   │
   ├──[mock=true]──→ 返回模拟响应 "[OpenAI:name] mock response..."
   │
   └──[mock=false]
         │
         ▼
   构建 HTTP POST 请求
   - endpoint: https://api.openai.com/v1/chat/completions
   - header: Authorization: Bearer <apiKey>
   - body: {"model":"gpt-4","messages":[{"role":"user","content":input}]}
         │
         ▼
   ┌─────────────────────────────┐
   │  for attempt in 0..maxRetries│
   │     send HTTP request        │
   │         │                    │
   │    ┌────┴────┐               │
   │    │ 2xx?   │               │
   │    ├──Yes──→→ 解析JSON响应  │
   │    │        │   返回content  │
   │    └──No───→│  retry?       │
   │             │  ┌─Yes─→sleep→│
   │             │  │  continue   │
   │             │  └──No──→throw │
   └─────────────┴────────────────┘
```

### 流程四：Spring Boot 自动配置流程

```
应用启动
   │
   ▼
┌──────────────────────────────────────────────┐
│ ModelAutoConfiguration.init()                │
│                                              │
│ for (ProviderConfig pc : config.providers)  │
│     if (!pc.isEnabled) continue              │  ← 检查 enabled
│     创建 ModelProviderConfig                 │
│     if (!autoDetectApiKey(cfg))             │  ← API Key 自动检测
│         log.warn("skipped")                 │  ← 无 Key 且非 mock 则跳过
│         continue                            │
│     添加到列表                               │
│                                              │
│ registry.addAll(factory.create(list))       │  ← 注册所有 Provider
└────────────────────┬───────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────┐
│ @Bean modelRouter()                          │
│   根据 routingStrategy 配置创建策略           │
│   routingStrategy=priority → PriorityBased   │
│   其他 → CostBased (默认)                     │
│   返回 ModelRouter(registry, strategy)        │
└──────────────────────────────────────────────┘
```

### 流程五：多策略选择逻辑

```
┌──────────────────────────────────────────────────────┐
│              RoutingStrategy.select()                 │
│                                                       │
│  ┌──────────────┐                                     │
│  │CostBased     │  providers.stream()                 │
│  │最低成本优先   │  .filter(isEnabled)                 │
│  └──────┬───────┘  .min(costPerToken)                │
│         │           → 返回成本最低的 Provider          │
│  ┌──────┴───────┐                                     │
│  │PriorityBased │  providers.stream()                 │
│  │最高优先级优先 │  .filter(isEnabled)                 │
│  └──────┬───────┘  .min(priority)                    │
│         │           → 返回 priority 最低的 Provider    │
│  ┌──────┴───────┐                                     │
│  │Fallback      │  providers.stream()                 │
│  │降级熔断      │  .filter(isEnabled)                 │
│  └──────┬───────┘  .min(priority)                    │
│         │           → 同 PriorityBased                │
│  ┌──────┴───────┐                                     │
│  │RoundRobin    │  按 cursor 轮询，cursor++            │
│  │轮询负载均衡  │  → 返回 cursor 位置的 Provider       │
│  └──────┬───────┘                                     │
│         │                                              │
│  ┌──────┴───────┐                                     │
│  │Capability    │  ctx.capability 匹配 Provider        │
│  │能力匹配      │  .getCapability() 标签               │
│  └──────────────┘  → 匹配则优先选，兜底成本最低        │
└──────────────────────────────────────────────────────┘
```

## 项目结构

```
rect-agent/
├── pom.xml                              # 父 POM（多模块聚合）
├── mvnw / mvnw.cmd                     # Maven Wrapper
├── AGENTS.md                            # Agent 开发规范
│
├── .github/workflows/
│   └── multi-model.yml                  # GitHub Actions CI/CD
│
├── openclaw-java/                       # WebSocket 网关模块
│   └── src/main/java/com/tlq/openclaw/
│       ├── OpenClawApplication.java
│       ├── gateway/                     # WebSocket 网关
│       ├── agent/                       # Agent 管理
│       ├── channel/                     # 渠道接入（Telegram等）
│       ├── tool/                        # 工具实现
│       └── skill/                       # 技能实现
│
├── rect-agent-core/                     # 核心模块
│   └── src/
│       ├── main/java/com/tlq/rectagent/
│       │   ├── RectAgentApplication.java            # Spring Boot 启动入口
│       │   │
│       │   ├── agent/                    # Agent 编排层
│       │   │   ├── CoordinatorAgent.java             # 主控 Agent (SequentialAgent)
│       │   │   ├── IntentRecognitionAgent.java       # 意图识别 Agent
│       │   │   ├── DynamicPromptAgent.java          # 动态提示词 Agent
│       │   │   ├── DataAnalysisAgent.java           # 数据分析 Agent
│       │   │   ├── SequentialAgentExecutor.java     # 顺序执行器
│       │   │   ├── SupervisorAgent.java            # 主控 Agent (旧版)
│       │   │   ├── AgentReflectionUtil.java        # 反射工具
│       │   │   ├── AgentDataContext.java          # 数据上下文
│       │   │   └── tools/                          # Agent 工具集
│       │   │       ├── AgentTool.java
│       │   │       ├── IntentTool.java
│       │   │       └── AnalysisTool.java
│       │   │
│       │   ├── hook/                     # Hook 体系
│       │   │   ├── ContextInjectionHook.java       # 上下文注入 (BEFORE_MODEL)
│       │   │   ├── ProfileInferenceHook.java       # 画像推断 (AFTER_MODEL)
│       │   │   └── HookConfiguration.java         # 框架 Hook 配置
│       │   │
│       │   ├── interceptor/              # 拦截器
│       │   │   ├── ModelProcessInterceptor.java   # 模型调用监控
│       │   │   └── ToolMonitoringInterceptor.java  # 工具执行监控
│       │   │
│       │   ├── context/                  # 上下文管理
│       │   │   ├── ContextLoader.java               # 分层上下文加载器
│       │   │   ├── TokenBudgetManager.java          # Token 预算管理
│       │   │   ├── CheckpointRecoveryManager.java   # 断点恢复管理
│       │   │   ├── ContextManager.java              # 上下文管理器
│       │   │   ├── MessagePurifier.java            # 消息净化
│       │   │   └── PromptVersionManager.java       # Prompt 版本管理
│       │   │
│       │   ├── profile/                  # 用户画像
│       │   │   └── ProfileInferenceService.java    # 画像推断服务
│       │   │
│       │   ├── model/                    # 模型路由层
│       │   │   ├── ModelProvider.java              # Provider 接口
│       │   │   ├── ModelProviderConfig.java        # Provider 配置
│       │   │   ├── DashScopeProvider.java         # 阿里云百炼实现
│       │   │   ├── OpenAIProvider.java             # OpenAI 实现
│       │   │   ├── AnthropicProvider.java         # Anthropic 实现
│       │   │   ├── ModelRegistry.java              # Provider 注册表
│       │   │   ├── ModelProviderFactory.java       # 工厂方法
│       │   │   ├── ModelRouter.java               # 路由决策器
│       │   │   ├── RoutingContext.java             # 路由上下文
│       │   │   ├── ModelRoutingStrategy.java       # 策略接口
│       │   │   ├── CostBasedStrategy.java          # 成本优先策略
│       │   │   ├── PriorityBasedStrategy.java     # 优先级策略
│       │   │   ├── FallbackStrategy.java           # 降级熔断策略
│       │   │   ├── RoundRobinStrategy.java        # 轮询负载策略
│       │   │   ├── CapabilityStrategy.java         # 能力匹配策略
│       │   │   └── ProviderResult.java             # 调用结果包装
│       │   │
│       │   ├── data/                     # 数据层
│       │   │   ├── entity/                           # 实体类
│       │   │   │   ├── ChatSession.java
│       │   │   │   ├── ChatMessage.java
│       │   │   │   ├── ProfileChange.java
│       │   │   │   ├── ConversationCheckpoint.java
│       │   │   │   └── ToolExecution.java
│       │   │   ├── mapper/                           # MyBatis Mapper
│       │   │   └── service/                         # 数据服务
│       │   │       ├── DataGovernanceService.java   # 数据治理入口
│       │   │       ├── ChatSessionService.java
│       │   │       ├── ChatMessageService.java
│       │   │       ├── ProfileChangeService.java
│       │   │       ├── ToolExecutionService.java
│       │   │       └── ConversationCheckpointService.java
│       │   │
│       │   ├── skill/                    # 技能层
│       │   │   ├── DocumentLearningSkill.java       # 文档学习技能
│       │   │   ├── SkillManager.java               # 技能管理器
│       │   │   ├── SkillController.java            # 技能控制器
│       │   │   ├── SkillInitializer.java            # 技能初始化器
│       │   │   ├── fetcher/                        # 文档获取
│       │   │   ├── parser/                         # 文档解析
│       │   │   ├── validator/                      # 文档校验
│       │   │   ├── learner/                        # LLM 学习
│       │   │   ├── executor/                       # 需求执行
│       │   │   └── depositor/                      # 结果沉淀
│       │   │
│       │   ├── config/                   # Spring 配置层
│       │   │   ├── ModelConfigProperties.java     # 配置属性
│       │   │   ├── ModelAutoConfiguration.java    # 自动配置类
│       │   │   ├── AgentModelSelector.java        # Agent 路由选择
│       │   │   ├── ChatModelFactory.java          # ChatModel 工厂
│       │   │   └── SkillConfig.java               # 技能配置
│       │   │
│       │   ├── memory/                   # 记忆层
│       │   │   ├── ShortTermMemoryManager.java    # 短期记忆
│       │   │   └── LongTermMemoryManager.java     # 长期记忆
│       │   │
│       │   ├── service/                  # 服务层
│       │   │   └── RectAgentService.java          # Agent 服务入口
│       │   │
│       │   ├── communication/            # 通信层
│       │   │   └── AgentCommunicationManager.java
│       │   │
│       │   ├── scheduler/                # 调度层
│       │   │   └── AgentScheduler.java            # Agent 调度器
│       │   │
│       │   ├── optimization/             # 优化层
│       │   │   └── PerformanceOptimizer.java
│       │   │
│       │   ├── filter/                   # 过滤器
│       │   │   └── TraceIdFilter.java            # TraceId 过滤器
│       │   │
│       │   ├── error/                    # 错误处理
│       │   │   └── ErrorHandler.java
│       │   │
│       │   └── tools/                    # 工具集
│       │       └── DataAnalysisTools.java        # 数据分析工具
│       │
│       ├── main/resources/
│       │   ├── application.yml                     # 主配置
│       │   ├── schema.sql                          # H2 建表
│       │   └── META-INF/spring/
│       │       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│       │
│       └── test/java/com/tlq/rectagent/           # 测试
│           ├── agent/
│           │   ├── CoordinatorAgentE2ETest.java   # Agent E2E 测试
│           │   ├── AgentReflectionUtilTest.java    # 反射工具测试
│           │   └── SupervisorAgentPerfTest.java   # 性能测试
│           ├── model/                              # 模型路由测试
│           ├── context/                            # 上下文测试
│           ├── data/                               # 数据层测试
│           └── skill/                              # 技能测试
│
├── docs/todo_task/
│   └── todo_task_v2.md                   # 任务追踪文档（v2 优化方案）
│
└── scripts/
    └── self_check.bat                    # Windows 本地检查脚本
```

## 快速开始

### 环境要求

- JDK 21+ (`JAVA_HOME` 指向 JDK 21)
- Maven 3.6+

### 构建

```bash
# 全量构建（跳过测试）
mvn -B -DskipTests package

# 运行测试
mvn -B test

# Windows 本地检查（需先设置 JAVA_HOME）
scripts\self_check.bat
```

### 配置

在 `application.yml` 中配置多模型路由，支持 Provider + Model 两层配置：

#### 1. Provider 配置（提供商层）

```yaml
spring:
  ai:
    dashscope:
      enabled: true
      api-key: ${DASHSCOPE_API_KEY:}

rectagent:
  model:
    default-model: dashscope-qwen-turbo
    routing-strategy: cost  # cost | priority

    # Provider 配置（提供商）
    providers:
      dashscope:           # 阿里云百炼
        enabled: true
        type: dashscope
        base-url: https://dashscope.aliyuncs.com
        api-key: ${DASHSCOPE_API_KEY:}
        timeout: 30000
        max-retries: 2

      openai:              # OpenAI 官方
        enabled: true
        type: openai
        base-url: https://api.openai.com/v1
        api-key: ${OPENAI_API_KEY:}
        timeout: 60000
        max-retries: 3

      anthropic:           # Anthropic
        enabled: false
        type: anthropic
        base-url: https://api.anthropic.com
        api-key: ${ANTHROPIC_API_KEY:}
        timeout: 60000
        max-retries: 3
```

#### 2. Model 配置（模型实例层）

```yaml
rectagent:
  model:
    # 模型实例配置
    models:
      dashscope-qwen-turbo:      # 阿里云低成本模型
        provider: dashscope
        model: qwen-turbo
        cost-per-token: 0.001
        priority: 1
        capabilities: intent,prompt,chat

      dashscope-qwen-plus:       # 阿里云高性能模型
        provider: dashscope
        model: qwen-plus
        cost-per-token: 0.01
        priority: 2
        capabilities: analysis,reasoning

      openai-gpt4o-mini:         # OpenAI 模型
        provider: openai
        model: gpt-4o-mini
        cost-per-token: 0.002
        priority: 3
        capabilities: analysis,reasoning,chat

      anthropic-haiku:           # Anthropic 模型
        provider: anthropic
        model: claude-3-haiku
        cost-per-token: 0.003
        priority: 4
        capabilities: analysis,chat
```

#### 3. Agent → Model 映射

```yaml
rectagent:
  model:
    # Agent 类型到模型的映射
    agent-model-mapping:
      intent_recognition_agent: dashscope-qwen-turbo
      dynamic_prompt_agent: dashscope-qwen-turbo
      data_analysis_agent: openai-gpt4o-mini
```

#### 4. 降级链配置

```yaml
rectagent:
  model:
    # 模型降级链：主模型失败时按顺序降级
    model-fallback-chains:
      openai-gpt4o-mini: dashscope-qwen-turbo,dashscope-qwen-plus
      anthropic-haiku: openai-gpt4o-mini,dashscope-qwen-turbo
```

#### 5. 熔断器配置

```yaml
rectagent:
  model:
    circuit-breaker:
      enabled: true
      error-rate-threshold: 0.5      # 50% 错误率触发熔断
      slow-call-duration-ms: 5000    # 5s 慢调用阈值
      slow-call-rate-threshold: 0.8  # 80% 慢调用率触发熔断
      min-call-count: 10             # 最少调用次数
      wait-duration-in-open-ms: 30000  # 熔断持续 30s
```

#### 6. 灰度发布配置

```yaml
rectagent:
  model:
    traffic-shifting:
      enabled: false
      rules:
        - agent: data_analysis_agent
          model: openai-gpt4o-mini
          percentage: 10     # 10% 流量切到新模型
        - agent: data_analysis_agent
          model: dashscope-qwen-turbo
          percentage: 90
```

#### 配置架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        rectagent.model                          │
├─────────────────────────────────────────────────────────────────┤
│  default-model: dashscope-qwen-turbo    # 默认模型               │
│  routing-strategy: cost                # 路由策略                │
├─────────────────────────────────────────────────────────────────┤
│  providers:  (提供商层)                                        │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ dashscope ──▶ base-url, api-key, timeout, max-retries      ││
│  │ openai    ──▶ base-url, api-key, timeout, max-retries      ││
│  │ anthropic ──▶ base-url, api-key, timeout, max-retries      ││
│  └─────────────────────────────────────────────────────────────┘│
├─────────────────────────────────────────────────────────────────┤
│  models:  (模型实例层)                                          │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ dashscope-qwen-turbo ──▶ provider: dashscope, cost: 0.001  ││
│  │ dashscope-qwen-plus  ──▶ provider: dashscope, cost: 0.01  ││
│  │ openai-gpt4o-mini    ──▶ provider: openai,    cost: 0.002  ││
│  └─────────────────────────────────────────────────────────────┘│
├─────────────────────────────────────────────────────────────────┤
│  agent-model-mapping:  (Agent → Model 映射)                     │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ intent_recognition_agent ──▶ dashscope-qwen-turbo         ││
│  │ dynamic_prompt_agent     ──▶ dashscope-qwen-turbo         ││
│  │ data_analysis_agent      ──▶ openai-gpt4o-mini           ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### 使用示例

#### 1. CoordinatorAgent 编排（顺序执行流水线）

```java
@Autowired
private CoordinatorAgent coordinatorAgent;

// 简单调用
String result = coordinatorAgent.processRequest("分析项目 test 的风险数据");

// 带会话上下文
String result = coordinatorAgent.processRequest("分析项目 test 的风险数据", sessionId, userId);

// 获取完整元数据
AgentResponse response = coordinatorAgent.processRequestWithMetadata(
    "分析项目 test 的风险数据", sessionId, userId);

// 访问各阶段结果
System.out.println(response.intentResult());      // 意图识别结果
System.out.println(response.generatedPrompt());    // 生成的提示词
System.out.println(response.analysisResult());    // 分析结果
System.out.println(response.agentOutputs());       // 所有 Agent 输出
```

#### 2. 直接使用 ModelRouter 路由

```java
@Autowired
private ModelRouter modelRouter;

String result = modelRouter.route("你好，请介绍一下自己");
```

#### 3. 自定义 AgentTool

```java
public class MyTool implements AgentTool {
    @Override
    public String getName() { return "my-tool"; }

    @Override
    public String apply(String input) {
        // 处理输入，返回结果
        return "处理后的: " + input;
    }
}

SupervisorAgent supervisor = new SupervisorAgent(router,
    Arrays.asList(new MyTool()));
```

#### 4. 按 Agent 类型路由（AgentModelSelector）

```java
Map<String, String> mapping = new HashMap<>();
mapping.put("intent", "dashscope-primary");
mapping.put("analysis", "openai-gpt4");

AgentModelSelector selector = new AgentModelSelector(registry, mapping);
ModelProvider p = selector.selectForAgentOrDefault("intent");
```

#### 5. 切换路由策略

```yaml
# 成本优先（默认）
rectagent.model.routingStrategy: cost

# 优先级优先
rectagent.model.routingStrategy: priority
```

## 路由策略说明

| 策略 | 选优规则 | 使用场景 |
|------|---------|---------|
| `CostBasedStrategy` | 选 `costPerToken` 最低 | 成本优化场景 |
| `PriorityBasedStrategy` | 选 `priority` 最低 | SLA 保障场景 |
| `FallbackStrategy` | 选 `priority` 最低 | 降级熔断场景 |
| `RoundRobinStrategy` | 轮询选择 | 负载均衡场景 |
| `CapabilityStrategy` | 匹配 `capability` 标签 | 多能力分发场景 |

## Provider 对比

| Provider | 端点 | 鉴权 | 重试 | 多端点 |
|----------|------|------|------|--------|
| DashScopeProvider | `/api/v1/services/...` | 无需 Key（可选） | ✅ maxRetries | ✅ endpoints 列表 |
| OpenAIProvider | `/v1/chat/completions` | Bearer Token | ✅ maxRetries | ❌ 单端点 |
| AnthropicProvider | `/v1/messages` | x-api-key | ✅ maxRetries | ❌ 单端点 |

## 测试

```
Tests run: 16 (单元测试), Failures: 0, Errors: 0, Skipped: 0

核心单元测试：
- CoordinatorAgentE2ETest: 9 测试
- SupervisorAgentPerfTest: 4 测试  
- AgentReflectionUtilTest: 3 测试

性能基线（mock 模式）：
- 单次调用延迟：~0.009ms
- 吞吐量：25000 req/s
- 并发负载（100并发）：avg 0.37ms，p99 5.03ms
```

## CI/CD

GitHub Actions 工作流（`.github/workflows/multi-model.yml`）自动执行：
- 每次 push/PR 运行 `mvn test`
- 使用 JDK 21 构建

## 扩展指南

### 新增 Provider

1. 实现 `ModelProvider` 接口
2. 在 `ModelProviderFactory.createFromConfig()` 中注册类型
3. 添加集成测试

### 新增路由策略

1. 实现 `ModelRoutingStrategy` 接口
2. 在 `ModelAutoConfiguration.modelRouter()` Bean 中注册

### 新增 AgentTool

1. 实现 `AgentTool` 接口（`getName()` + `apply(input)`）
2. 注入到 `SupervisorAgent` 的工具列表中

## 类架构图

### 模块分层架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           RectAgentApplication                              │
│                          (Spring Boot 启动入口)                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        │                           │                           │
        ▼                           ▼                           ▼
┌───────────────┐          ┌───────────────┐          ┌───────────────┐
│   Controller  │          │     Agent    │          │    Skill     │
│     Layer     │          │     Layer    │          │    Layer     │
│  (API 入口)   │          │  (编排逻辑)   │          │ (文档学习)    │
└───────────────┘          └───────────────┘          └───────────────┘
        │                           │                           │
        └───────────────────────────┼───────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Hook / Interceptor Layer                            │
│                    (上下文注入、画像推断、监控拦截)                            │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        │                           │                           │
        ▼                           ▼                           ▼
┌───────────────┐          ┌───────────────┐          ┌───────────────┐
│   Context     │          │   Profile     │          │    Model      │
│   Layer       │          │   Layer       │          │   Layer       │
│  (分层上下文)  │          │  (画像管理)   │          │  (路由/调用)   │
└───────────────┘          └───────────────┘          └───────────────┘
        │                           │                           │
        └───────────────────────────┼───────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Data / Persistence Layer                           │
│                       (H2 + MyBatis-Plus 持久化)                            │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Agent 编排层类图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CoordinatorAgent                                 │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  • processRequest(userInput)                                         │   │
│  │  • processRequest(userInput, sessionId, userId)                      │   │
│  │  • processRequestWithMetadata() → AgentResponse                      │   │
│  │  └─ AgentResponse record (content, sessionId, userId, intentResult,  │   │
│  │                            generatedPrompt, analysisResult,           │   │
│  │                            agentOutputs)                              │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                      │                                      │
│                                      ▼                                      │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    SequentialAgent (框架)                             │   │
│  │                     顺序执行子 Agent 链                                │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                      │                                      │
│          ┌──────────────────────────┼──────────────────────────┐          │
│          │                          │                          │          │
│          ▼                          ▼                          ▼          │
│  ┌────────────────┐    ┌────────────────────┐    ┌────────────────────┐   │
│  │IntentRecognition│    │  DynamicPrompt     │    │  DataAnalysis      │   │
│  │    Agent       │    │     Agent          │    │     Agent         │   │
│  │                │    │                    │    │                    │   │
│  │ outputKey:     │    │ outputKey:        │    │ outputKey:        │   │
│  │ user_intent    │───▶│ generated_prompt   │───▶│ analysis_result   │   │
│  └────────────────┘    └────────────────────┘    └────────────────────┘   │
│          │                          │                          │          │
│          └──────────────────────────┼──────────────────────────┘          │
│                                     │                                      │
│                                     ▼                                      │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    Hook / Interceptor Chain                           │   │
│  │  ┌─────────────┐  ┌─────────────────┐  ┌──────────────────┐        │   │
│  │  │ContextInject│  │ Summarization    │  │ ProfileInference │        │   │
│  │  │ionHook      │  │ Hook            │  │ Hook             │        │   │
│  │  │(BEFORE_MODEL)│  │ (Token压缩)     │  │ (AFTER_MODEL)    │        │   │
│  │  └─────────────┘  └─────────────────┘  └──────────────────┘        │   │
│  │  ┌─────────────┐  ┌─────────────────┐  ┌──────────────────┐        │   │
│  │  │ModelCall   │  │ ModelProcess    │  │ ToolMonitoring   │        │   │
│  │  │LimitHook   │  │ Interceptor     │  │ Interceptor      │        │   │
│  │  │(限流)      │  │ (日志监控)       │  │ (性能监控)        │        │   │
│  │  └─────────────┘  └─────────────────┘  └──────────────────┘        │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 分层上下文体系

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              ContextLoader                                   │
│                                                                             │
│  loadContext(sessionId, userId)                                             │
│       │                                                                      │
│       ├──▶ L1_HOT (热层) ───────────────────────────────────────┐          │
│       │    最近 N 条消息 (l1.hot-turns)                           │          │
│       │    • ChatMessageService.getRecentMessages()              │          │
│       │    • 用于即时对话理解                                      │          │
│       │                                                            │          │
│       ├──▶ L2_WARM (温层) ──────────────────────────────────────┐          │
│       │    会话摘要快照 (session.summarySnapshot)                  │          │
│       │    • ChatSessionService.getSessionById()                 │          │
│       │    • 用于跨轮次上下文理解                                  │          │
│       │                                                            │          │
│       └──▶ L3_PROFILE (画像层) ──────────────────────────────────┐          │
│            用户画像标签 (profile_changes 表)                       │          │
│            • ProfileChangeService.getChangesByUserId()           │          │
│            • extractProfileTags(): 最新值优先                      │          │
│            • 用于个性化服务                                         │          │
│                                                                     │          │
│  ┌──────────────────────────────────────────────────────────────┐ │          │
│  │                      Context (内部类)                          │ │          │
│  │  • sessionId / userId / traceId                              │ │          │
│  │  • hotMessages: List<ChatMessage>                            │ │          │
│  │  • sessionSummary: String                                    │ │          │
│  │  • profileTags: List<String>                                 │ │          │
│  │  • checkpointState: 断点恢复数据                              │ │          │
│  │  • requiresRecovery: 是否需要断点恢复                           │ │          │
│  └──────────────────────────────────────────────────────────────┘ │          │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Hook 体系详解

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Hook 执行时机                                    │
└─────────────────────────────────────────────────────────────────────────────┘

                        ┌─────────────────────────────────────┐
                        │           ReactAgent Loop            │
                        │                                      │
  BEFORE_MODEL ─────────▶│  ┌─────────────────────────────────┐│
  ┌────────────────┐     │  │  AgentLlmNode (LLM 调用)         ││
  │ ContextInject  │     │  │                                  ││
  │ ionHook        │     │  │  • 构建 ChatClientRequest        ││
  │                │     │  │  • BEFORE_MODEL Hook 执行         ││
  │ • 注入用户画像  │     │  │  • LLM 调用                      ││
  │ • 注入会话摘要  │     │  │  • AFTER_MODEL Hook 执行         ││
  │ • 注入热消息   │     │  │                                  ││
  └────────────────┘     │  └─────────────────────────────────┘│
                         │              │                       │
  AFTER_MODEL ───────────│              │                       │
  ┌────────────────┐     │              ▼                       │
  │ProfileInference│     │  ┌─────────────────────────────────┐│
  │    Hook        │     │  │  AgentToolNode (Tool 调用)       ││
  │                │     │  │                                  ││
  │ • 提取对话内容  │     │  │  • BEFORE_TOOL Hook 执行         ││
  │ • 推断兴趣领域  │     │  │  • Tool 执行                     ││
  │ • 推断专业水平  │     │  │  • AFTER_TOOL Hook 执行          ││
  │ • 推断数据范围  │     │  └─────────────────────────────────┘│
  └────────────────┘     │              │                       │
                         │              ▼                       │
                         └─────────────────────────────────────┘
                               │              ▲
                               ▼              │
                        ┌─────────────────────────────────────┐
                        │            END                        │
                        └─────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                           Hook 类型对比                                       │
├─────────────────┬───────────────────────────────────────────────────────────┤
│ MessagesModelHook│ BEFORE_MODEL / AFTER_MODEL，修改消息列表                  │
│ ModelHook       │ BEFORE_MODEL / AFTER_MODEL，访问/修改模型响应                │
│ ToolHook        │ BEFORE_TOOL / AFTER_TOOL，修改工具调用                      │
│ AgentHook       │ BEFORE_AGENT / AFTER_AGENT，Agent 执行前后                   │
└─────────────────┴───────────────────────────────────────────────────────────┘
```

### 数据治理层

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           DataGovernanceService                              │
│                                                                             │
│  会话管理 ──────────▶ ChatSessionService                                      │
│  │                   • startNewSession()                                     │
│  │                   • getOrCreateSession()                                 │
│  │                   • updateSessionSummary()                                │
│  │                   • endSession()                                          │
│  │                                                                      │
│  消息记录 ──────────▶ ChatMessageService                                     │
│  │                   • recordUserMessage()                                  │
│  │                   • recordAssistantMessage()                             │
│  │                   • getRecentMessages()                                  │
│  │                   • getLatestCheckpointBySessionId()                     │
│  │                                                                      │
│  工具执行 ──────────▶ ToolExecutionService                                    │
│  │                   • createExecution()                                    │
│  │                   • updateExecutionSuccess()                             │
│  │                   • updateExecutionFailed()                              │
│  │                                                                      │
│  画像变更 ──────────▶ ProfileChangeService                                    │
│  │                   • recordChange()                                        │
│  │                   • getChangesByUserId()                                 │
│  │                                                                      │
│  断点恢复 ──────────▶ ConversationCheckpointService                           │
│                        • createCheckpoint()                                 │
│                        • getLatestCheckpoint()                              │
│                        • markAsResumed()                                    │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              实体关系图                                       │
│                                                                             │
│  ┌─────────────┐       ┌─────────────┐       ┌─────────────────────┐      │
│  │ ChatSession │───1:N─▶│ ChatMessage │       │  ProfileChange      │      │
│  │             │       │             │       │                     │      │
│  │ • id        │       │ • id        │       │ • id                │      │
│  │ • userId    │       │ • sessionId │       │ • userId            │      │
│  │ • traceId   │       │ • role      │       │ • fieldName         │      │
│  │ • summary   │       │ • content   │       │ • oldValue          │      │
│  └─────────────┘       └─────────────┘       │ • newValue          │      │
│         │                     ▲               │ • reasoning         │      │
│         │                     │               └─────────────────────┘      │
│         │              ┌──────┴──────┐                                    │
│         │              │  断点记录    │       ┌─────────────────────┐      │
│         │              │             │       │ ConversationCheck-  │      │
│         │              │ • sessionId │       │ point               │      │
│         │              │ • stepIndex │       │                     │      │
│         │              │ • stateData│       │ • id                │      │
│         │              └─────────────┘       │ • sessionId         │      │
│         │                                   │ • stepIndex         │      │
│         └──────────────────────────────────▶│ • stateData         │      │
│                                             └─────────────────────┘      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 模型路由层

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              ModelRouter                                    │
│                                                                             │
│  route(input)                                                               │
│       │                                                                     │
│       ▼                                                                     │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                    RoutingStrategy.select()                           │    │
│  │                                                                     │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌───────────┐ │    │
│  │  │CostBased    │  │PriorityBased│  │ RoundRobin  │  │Capability │ │    │
│  │  │(最低成本)    │  │(最高优先)   │  │ (轮询)      │  │(能力匹配)  │ │    │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └─────┬─────┘ │    │
│  │         │                 │                 │               │       │    │
│  └─────────┴────────────────┴─────────────────┴───────────────┴───────┘    │
│                                    │                                        │
│                                    ▼                                        │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                       ModelProviderFactory                            │    │
│  │   根据配置创建 Provider (DashScope / OpenAI / Anthropic)              │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                    │                                        │
│       ┌────────────────────────────┼────────────────────────────┐        │
│       │                            │                            │        │
│       ▼                            ▼                            ▼        │
│  ┌────────────┐            ┌────────────┐            ┌────────────┐     │
│  │DashScope  │            │  OpenAI    │            │ Anthropic  │     │
│  │Provider   │            │  Provider  │            │ Provider   │     │
│  │           │            │            │            │            │     │
│  │• 多端点   │            │• 单端点    │            │• 单端点    │     │
│  │• API Key  │            │• Bearer    │            │• x-api-key│     │
│  └────────────┘            └────────────┘            └────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Skill 文档学习流水线

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        DocumentLearningSkill                                 │
│                                                                             │
│  processRequest(documentUrls, userRequirement)                              │
│       │                                                                     │
│       ├──▶ DocumentFetcher (获取) ────────────────────────────────────┐    │
│       │    • PdfFetcher (.pdf)                                          │    │
│       │    • OfficeFetcher (.docx/.xlsx/.pptx)                         │    │
│       │    • HtmlFetcher (.html/.htm)                                   │    │
│       │    • CompositeDocumentFetcher (组合)                             │    │
│       │                                                                   │    │
│       ├──▶ DocumentParser (解析) ─────────────────────────────────────┐  │
│       │    • PdfParser                                                   │  │
│       │    • OfficeParser                                                │  │
│       │    • HtmlParser                                                   │  │
│       │    • CompositeDocumentParser (组合)                              │  │
│       │                                                                   │  │
│       ├──▶ DocumentValidator (校验) ──────────────────────────────────┐  │
│       │    • DefaultDocumentValidator                                   │  │
│       │                                                                   │  │
│       ├──▶ LMLearner (学习) ──────────────────────────────────────────┐  │
│       │    • DashScopeLearner (阿里云百炼)                              │  │
│       │                                                                   │  │
│       ├──▶ RequirementExecutor (需求执行) ─────────────────────────────┐   │
│       │    • DashScopeExecutor                                          │   │
│       │                                                                   │   │
│       └──▶ DocumentDepositor (沉淀) ──────────────────────────────────┐   │
│            • DefaultDocumentDepositor                                    │   │
│                                                                           │   │
│            输出: 执行结果 (基于文档内容的需求实现)                          │   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 包结构速查

| 包路径 | 职责 | 核心类 |
|--------|------|--------|
| `agent/` | Agent 编排 | `CoordinatorAgent`, `SequentialAgentExecutor`, `*Agent` |
| `hook/` | Hook 体系 | `ContextInjectionHook`, `ProfileInferenceHook`, `HookConfiguration` |
| `interceptor/` | 拦截器 | `ModelProcessInterceptor`, `ToolMonitoringInterceptor` |
| `context/` | 上下文管理 | `ContextLoader`, `TokenBudgetManager`, `CheckpointRecoveryManager` |
| `profile/` | 画像管理 | `ProfileInferenceService` |
| `model/` | 模型路由 | `ModelRouter`, `ModelProvider`, `*Strategy` |
| `data/service/` | 数据服务 | `DataGovernanceService`, `ChatSessionService`, `ChatMessageService` |
| `data/entity/` | 实体 | `ChatSession`, `ChatMessage`, `ProfileChange`, `ConversationCheckpoint` |
| `skill/` | 技能 | `DocumentLearningSkill`, `SkillManager`, `SkillController` |
| `config/` | 配置 | `ModelAutoConfiguration`, `ChatModelFactory` |
| `memory/` | 记忆 | `ShortTermMemoryManager`, `LongTermMemoryManager` |
| `communication/` | 通信 | `AgentCommunicationManager` |
| `scheduler/` | 调度 | `AgentScheduler` |
| `optimization/` | 优化 | `PerformanceOptimizer` |
| `filter/` | 过滤器 | `TraceIdFilter` |
| `error/` | 错误处理 | `ErrorHandler` |

## 许可证

MIT
