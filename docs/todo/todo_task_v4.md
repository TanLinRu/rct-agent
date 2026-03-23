# 多模型 Agent 路由系统 - 实现计划 (v4)

> **版本**: 0.0.1-SNAPSHOT  
> **更新**: 2026-03-23  
> **状态**: 进行中

## 目标

实现按 Agent 类型选择不同 ChatModel 的能力，支持：
- 静态配置映射（配置文件优先）
- 动态策略路由（成本/能力匹配）
- 模型级别降级链
- 熔断器（错误率/慢调用监控）
- 灰度发布（按比例切流量）

## 架构设计

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           CoordinatorAgent                                       │
│                          (调用入口)                                              │
└─────────────────────────────────────┬───────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          AgentModelRouter                                        │
│   • 静态映射优先 → 策略路由 → 降级链                                              │
└─────────────────────────────────────┬───────────────────────────────────────────┘
                                      │
        ┌─────────────────────────────┼─────────────────────────────┐
        │                             │                             │
        ▼                             ▼                             ▼
┌───────────────────┐    ┌───────────────────┐    ┌───────────────────┐
│     DashScope     │    │      OpenAI      │    │    Anthropic      │
│    ModelPool      │    │    ModelPool    │    │    ModelPool      │
│                   │    │                   │    │                   │
│ qwen-turbo ──────┼───▶│  gpt-4o-mini ───┼───▶│ claude-3-haiku    │
│ qwen-plus ────────┘    │  gpt-4 ─────────┘    │                   │
└───────────────────┘    └───────────────────┘    └───────────────────┘
        │                             │                             │
        └─────────────────────────────┼─────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          CircuitBreakerManager                                    │
│   • 错误率监控  • 慢调用监控  • 自动熔断  • 恢复重试                               │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## 用户选择

| # | 选择 | 说明 |
|---|------|------|
| 1 | 降级链：模型级别 | openai → dashscope（而非 Agent 级别） |
| 2 | ChatModel：单例 | 同模型只创建一个 ChatModel 实例 |
| 3 | 熔断告警：仅日志 | 不触发外部通知 |
| 4 | 灰度发布：按比例 | 支持按百分比切流量 |

## 配置设计

```yaml
rectagent:
  model:
    default-model: dashscope-qwen-turbo
    routing-strategy: capability  # cost | priority | capability
    
    providers:
      dashscope:
        enabled: true
        base-url: https://dashscope.aliyuncs.com
        api-key: ${DASHSCOPE_API_KEY:}
        timeout: 30000
        max-retries: 2
      openai:
        enabled: false
        base-url: https://api.openai.com/v1
        api-key: ${OPENAI_API_KEY:}
        timeout: 60000
        max-retries: 3
      anthropic:
        enabled: false
        base-url: https://api.anthropic.com
        api-key: ${ANTHROPIC_API_KEY:}
        timeout: 60000
        max-retries: 3
    
    models:
      dashscope-qwen-turbo:
        provider: dashscope
        model: qwen-turbo
        cost-per-token: 0.001
        priority: 1
        capabilities: intent,prompt,chat
        
      dashscope-qwen-plus:
        provider: dashscope
        model: qwen-plus
        cost-per-token: 0.01
        priority: 2
        capabilities: analysis,reasoning
        
      openai-gpt4o-mini:
        provider: openai
        model: gpt-4o-mini
        cost-per-token: 0.002
        priority: 3
        capabilities: analysis,reasoning,chat
        
      anthropic-haiku:
        provider: anthropic
        model: claude-3-haiku
        cost-per-token: 0.003
        priority: 4
        capabilities: analysis,chat
    
    agent-model-mapping:
      intent_recognition_agent: dashscope-qwen-turbo
      dynamic_prompt_agent: dashscope-qwen-turbo
      data_analysis_agent: openai-gpt4o-mini
    
    model-fallback-chains:
      openai-gpt4o-mini: dashscope-qwen-turbo,dashscope-qwen-plus
      anthropic-haiku: openai-gpt4o-mini,dashscope-qwen-turbo
    
    circuit-breaker:
      enabled: true
      error-rate-threshold: 0.5
      slow-call-duration-ms: 5000
      slow-call-rate-threshold: 0.8
      min-call-count: 10
      wait-duration-in-open-ms: 30000
    
    traffic-shifting:
      enabled: true
      rules:
        - agent: data_analysis_agent
          model: openai-gpt4o-mini
          percentage: 10
        - agent: data_analysis_agent
          model: dashscope-qwen-turbo
          percentage: 90
```

## 文件清单

### 新增文件

| 顺序 | 文件路径 | 类名 | 职责 |
|------|---------|------|------|
| 1 | `model/config/CircuitBreakerProperties.java` | CircuitBreakerProperties | 熔断配置 |
| 2 | `model/config/ModelInstanceConfig.java` | ModelInstanceConfig | 模型实例配置 |
| 3 | `model/config/TrafficShiftingRule.java` | TrafficShiftingRule | 灰度规则 |
| 4 | `model/pool/ModelInstance.java` | ModelInstance | 模型实例 |
| 5 | `model/circuit/CircuitBreaker.java` | CircuitBreaker | 熔断器 |
| 6 | `model/circuit/CircuitBreakerManager.java` | CircuitBreakerManager | 熔断管理 |
| 7 | `model/pool/ChatModelPool.java` | ChatModelPool | ChatModel 池 |
| 8 | `model/routing/CostRoutingStrategy.java` | CostRoutingStrategy | 成本策略 |
| 9 | `model/routing/CapabilityRoutingStrategy.java` | CapabilityRoutingStrategy | 能力策略 |
| 10 | `model/router/AgentModelRouter.java` | AgentModelRouter | 路由核心 |
| 11 | `model/router/TrafficShiftingRouter.java` | TrafficShiftingRouter | 灰度路由 |

### 修改文件

| 顺序 | 文件路径 | 改动 |
|------|---------|------|
| 1 | `application.yml` | 新增完整配置 |
| 2 | `config/ModelConfigProperties.java` | 新增配置属性 |
| 3 | `config/ModelAutoConfiguration.java` | 集成新组件 |
| 4 | `agent/IntentRecognitionAgent.java` | 注入 AgentModelRouter |
| 5 | `agent/DynamicPromptAgent.java` | 注入 AgentModelRouter |
| 6 | `agent/DataAnalysisAgent.java` | 注入 AgentModelRouter |

### 测试文件

| 文件 | 测试场景 |
|------|---------|
| `CircuitBreakerTest.java` | 初始状态、错误率熔断、慢调用熔断、半开恢复 |
| `AgentModelRouterTest.java` | 静态映射、策略路由、降级链 |
| `TrafficShiftingRouterTest.java` | 灰度分发、百分比验证 |

## 实现阶段

### Phase 1: 配置与基础设施

- [x] 创建配置类
- [ ] 创建模型实例类
- [ ] 创建熔断器
- [ ] 创建熔断管理器
- [ ] 创建 ChatModel 池

### Phase 2: 路由核心

- [ ] 实现 AgentModelRouter
- [ ] 实现 CostRoutingStrategy
- [ ] 实现 CapabilityRoutingStrategy
- [ ] 实现 TrafficShiftingRouter

### Phase 3: 配置集成

- [ ] 修改 ModelConfigProperties
- [ ] 修改 ModelAutoConfiguration
- [ ] 更新 application.yml

### Phase 4: Agent 改造

- [ ] 修改 IntentRecognitionAgent
- [ ] 修改 DynamicPromptAgent
- [ ] 修改 DataAnalysisAgent

### Phase 5: 测试

- [ ] CircuitBreakerTest
- [ ] AgentModelRouterTest
- [ ] TrafficShiftingRouterTest
- [ ] 集成测试

## 熔断器状态流转

```
           ┌─────────────────────────────────────┐
           │                                     │
           │   ┌─────────────────────────────┐   │
           │   │         CLOSED             │   │
           │   │    正常调用，计数错误       │   │
           │   │                             │   │
           │   │  错误率 > 50%? ───YES──▶ OPEN│  │
           │   │         │                  │   │
           │   │         NO                 │   │
           │   │         │                  │   │
           │   └─────────┼──────────────────┘   │
           │             │                       │
           │             │ 等待 30s            │
           │             │                       │
           │             ▼                       │
           │   ┌─────────────────────────────┐  │
           │   │        HALF_OPEN           │  │
           │   │    允许部分请求测试         │  │
           │   │                             │  │
           │   │  成功? ───YES──▶ CLOSED    │  │
           │   │  失败? ───YES──▶ OPEN      │  │
           │   └─────────────────────────────┘  │
           │                                     │
           └─────────────────────────────────────┘
```

## 调用流程

```
Agent.getAgent()
       │
       ▼
AgentModelRouter.getChatModel(agentName)
       │
       ├──▶ 查找 agent-model-mapping 配置
       │
       ├──▶ 如果配置了具体模型 → 直接返回该 ChatModel
       │
       ├──▶ 如果模型不可用 → 按 model-fallback-chains 降级
       │
       └──▶ 如果无静态映射 → 按策略路由 (capability/cost)
              │
              └──▶ TrafficShiftingRouter 检查灰度规则
```

## 完成标准

1. 所有配置可从 application.yml 读取
2. Agent 可通过 AgentModelRouter 获取不同的 ChatModel
3. 熔断器可正确监控错误率和慢调用
4. 灰度规则可按百分比分发流量
5. 单元测试全部通过
6. 编译无错误

## 更新日志

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-03-23 | v4.0 | 初始版本，规划完成 |
