# Rect Agent - 多模型智能体路由系统

> **版本**: 0.0.1-SNAPSHOT
> **更新**: 2026-03-22

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
│                      SupervisorAgent                           │
│   编排 AgentTool 流水线，接收输入后依次调用工具链，最后路由   │
└─────────────────────────┬────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          │               │               │
          ▼               ▼               ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │IntentTool│    │Analysis- │    │ 更多Tool │
    │(意图识别) │    │Tool(分析)│    │ (可扩展) │
    └────┬─────┘    └────┬─────┘    └──────────┘
         │               │
         └───────┬───────┘
                 │ 累积结果
                 ▼
         ┌──────────────────┐
         │   ModelRouter     │  根据策略选择最优 Provider
         └────────┬─────────┘
                  │
     ┌────────────┼────────────┐
     │            │            │
     ▼            ▼            ▼
┌─────────┐  ┌─────────┐  ┌──────────┐
│DashScope│  │ OpenAI  │  │Anthropic │
│(阿里云)  │  │ (官方)  │  │ (Anthropic)│
└─────────┘  └─────────┘  └──────────┘
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
│       │   ├── model/                   # 模型路由层
│       │   │   ├── ModelProvider.java              # Provider 接口
│       │   │   ├── ModelProviderConfig.java        # Provider 配置
│       │   │   ├── DashScopeProvider.java          # 阿里云百炼实现
│       │   │   ├── OpenAIProvider.java              # OpenAI 实现
│       │   │   ├── AnthropicProvider.java          # Anthropic 实现
│       │   │   ├── ModelRegistry.java               # Provider 注册表
│       │   │   ├── ModelProviderFactory.java        # 工厂方法
│       │   │   ├── ModelRouter.java                 # 路由决策器
│       │   │   ├── RoutingContext.java              # 路由上下文
│       │   │   ├── ModelRoutingStrategy.java        # 策略接口
│       │   │   ├── CostBasedStrategy.java           # 成本优先策略
│       │   │   ├── PriorityBasedStrategy.java       # 优先级策略
│       │   │   ├── FallbackStrategy.java            # 降级熔断策略
│       │   │   ├── RoundRobinStrategy.java          # 轮询负载策略
│       │   │   ├── CapabilityStrategy.java          # 能力匹配策略
│       │   │   └── ProviderResult.java              # 调用结果包装
│       │   │
│       │   ├── agent/                    # 智能体编排层
│       │   │   ├── SupervisorAgent.java              # 主控 Agent
│       │   │   ├── AgentReflectionUtil.java         # 反射读取 Agent 名称
│       │   │   └── tools/
│       │   │       ├── AgentTool.java               # 工具接口
│       │   │       ├── IntentTool.java              # 意图识别工具
│       │   │       └── AnalysisTool.java           # 数据分析工具
│       │   │
│       │   └── config/                   # Spring 配置层
│       │       ├── ModelConfigProperties.java      # 配置属性（YAML绑定）
│       │       ├── ModelAutoConfiguration.java       # 自动配置类
│       │       └── AgentModelSelector.java          # 按 Agent 类型路由
│       │
│       ├── main/resources/
│       │   └── META-INF/spring/
│       │       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│       │                                       # Spring Boot 3 自动发现
│       └── test/java/com/tlq/rectagent/       # 36 个测试用例
│           ├── model/
│           │   ├── ModelRouterTest.java
│           │   ├── DashScopeProviderIntegrationTest.java
│           │   ├── DashScopeProviderRetryTest.java
│           │   ├── DashScopeProviderMultiEndpointTest.java
│           │   ├── OpenAIProviderIntegrationTest.java
│           │   ├── OpenAIProviderBoundaryTest.java
│           │   ├── AnthropicProviderIntegrationTest.java
│           │   ├── CostBasedStrategyTest.java
│           │   ├── PriorityBasedStrategyTest.java
│           │   ├── FallbackStrategyTest.java
│           │   ├── RoundRobinStrategyTest.java
│           │   ├── CapabilityStrategyTest.java
│           │   └── ModelProviderFactoryTest.java
│           ├── agent/
│           │   ├── SupervisorAgentTest.java
│           │   ├── SupervisorAgentPerfTest.java
│           │   └── AgentReflectionUtilTest.java
│           └── config/
│               ├── ModelAutoConfigurationTest.java
│               └── AgentModelSelectorTest.java
│
├── docs/todo_task/
│   └── todo_task_v3.md                   # 任务追踪文档（v3.9）
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

在 `application.yml` 中配置多模型路由：

```yaml
rectagent:
  model:
    routingStrategy: cost  # cost | priority（默认 cost）
    providers:
      # DashScope：阿里云百炼，多端点容错
      - name: dashscope-primary
        enabled: true
        type: dashscope
        model: qwen-turbo
        apiKey: ${DASHSCOPE_API_KEY:}
        costPerToken: 0.001
        priority: 1
        capability: chat
        mock: false
        endpoints:
          - https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation
        maxRetries: 2
        retryDelayMs: 500

      # OpenAI：官方 API，支持 endpoint 覆盖
      - name: openai-gpt4
        enabled: true
        type: openai
        model: gpt-4o-mini
        apiKey: ${OPENAI_API_KEY:}
        costPerToken: 0.002
        priority: 2
        capability: chat
        mock: false
        endpoint: https://api.openai.com/v1/chat/completions
        maxRetries: 2
        retryDelayMs: 500

      # Anthropic：Claude 系列
      - name: anthropic-claude
        enabled: false
        type: anthropic
        model: claude-3-haiku
        apiKey: ${ANTHROPIC_API_KEY:}
        costPerToken: 0.003
        priority: 3
        capability: chat
        mock: false
        maxRetries: 2
        retryDelayMs: 500
```

### 使用示例

#### 1. SupervisorAgent 编排（完整流水线）

```java
@Autowired
private ModelRouter modelRouter;

// 构建 SupervisorAgent
SupervisorAgent supervisor = new SupervisorAgent(modelRouter, Arrays.asList(
    new IntentTool("intent"),
    new AnalysisTool("analysis")
));

// 调用
String result = supervisor.invoke("分析项目 test 的风险数据");
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
Tests run: 36, Failures: 0, Errors: 0, Skipped: 0

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

## 许可证

MIT
