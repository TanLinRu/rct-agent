# Rect Agent - 多智能体数据分析系统

## 项目简介

Rect Agent 是一个基于 Spring AI Alibaba 实现的多智能体数据分析系统，专为处理复杂的数据分析任务而设计。系统采用多智能体架构，包含用户查询意图识别、动态提示词生成、数据分析和消息优化等核心功能。

## 技术栈

- **后端框架**：Spring Boot 3.3.5
- **Java 版本**：Java 21
- **AI 框架**：Spring AI Alibaba Agent Framework 1.1.2.0
- **AI 模型**：DashScope (阿里云百炼)
- **构建工具**：Maven

## 核心功能

### 1. 多智能体架构

- **用户查询意图智能体**：分析用户输入，识别查询意图
- **动态提示词智能体**：根据上下文生成优化的提示词
- **数据分析智能体**：执行具体的数据分析任务
- **消息优化智能体**：处理消息记录和压缩存储
- **协调智能体**：管理多智能体之间的协作和调度

### 2. 工具与记忆系统

- **数据分析工具**：提供多种数据分析功能
- **短期记忆管理**：管理对话历史和会话状态
- **长期记忆管理**：管理用户画像、偏好等长期信息
- **消息压缩存储**：优化消息存储和检索

### 3. 通信与调度

- **智能体通信机制**：实现智能体之间的消息传递
- **智能体调度系统**：管理和协调多个智能体的执行
- **上下文传递**：确保智能体之间的信息共享

### 4. 系统优化

- **性能优化**：智能体实例缓存、批量处理等
- **错误处理**：异常捕获和容错机制
- **安全管理**：API 密钥环境变量配置

## 功能差异

### 4.1 与同类系统的差异

- **基于 Spring AI Alibaba**：使用最新的 Spring AI Alibaba Agent Framework 1.1.2.0，提供更强大的智能体支持
- **智能体间数据传递**：采用 Instruction 占位符机制，实现智能体间的无缝数据传递
- **智能体实例缓存**：实现智能体实例缓存，减少重复创建的开销，提高系统性能
- **完整的多智能体编排**：使用 SequentialAgent 实现智能体的顺序执行，确保流程的连贯性
- **文档学习能力**：集成文档学习技能，支持从文档中学习知识并实现用户需求

### 4.2 优势

- **模块化设计**：采用模块化设计，便于扩展和维护
- **性能优化**：实现智能体实例缓存，提高系统性能
- **灵活性**：支持多种智能体协作模式，适应不同的业务场景
- **可扩展性**：易于添加新的智能体和工具
- **安全性**：使用环境变量管理 API 密钥，提高安全性

### 4.3 劣势

- **依赖 DashScope**：目前主要依赖阿里云的 DashScope 模型，对其他模型的支持有限
- **配置复杂**：多智能体系统的配置相对复杂，需要一定的技术知识
- **资源消耗**：多智能体系统可能消耗较多的计算资源

## 项目结构

```
rect-agent/
├── src/
│   ├── main/
│   │   ├── java/com/tlq/rectagent/
│   │   │   ├── agent/         # 智能体实现
│   │   │   ├── communication/  # 通信管理
│   │   │   ├── context/        # 上下文管理
│   │   │   ├── error/          # 错误处理
│   │   │   ├── interceptor/    # 拦截器
│   │   │   ├── memory/          # 记忆管理
│   │   │   ├── optimization/    # 性能优化
│   │   │   ├── scheduler/       # 智能体调度
│   │   │   ├── service/         # 服务层
│   │   │   ├── skill/           # 技能实现
│   │   │   ├── tools/           # 工具类
│   │   │   └── RectAgentApplication.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/tlq/rectagent/
│           ├── skill/
│           ├── MultiAgentTest.java
│           └── RectAgentApplicationTests.java
├── .trae/documents/            # 项目文档
├── docs/                       # 项目文档
├── pom.xml
└── README.md
```

## 快速开始

### 环境要求

- **JDK**: 21 (D:\software\jdk-21.0.8)
- Maven 3.8+
- DashScope API Key (阿里云百炼)

### 配置步骤

1. **配置 API Key（二选一）**

   ```bash
   # 方式1: 环境变量（推荐）
   set AI_DASHSCOPE_API_KEY=your_api_key_here

   # 方式2: application.yml 配置
   # spring:
   #   ai:
   #     dashscope:
   #       api-key: your_api_key_here
   ```

2. **构建项目**

   ```bash
   mvn clean package
   ```

3. **运行应用**

   ```bash
   java -jar target/rect-agent-0.0.1-SNAPSHOT.jar
   ```

## 使用示例

### 1. 协调智能体使用

```java
@Autowired
private CoordinatorAgent coordinatorAgent;

// 处理用户请求
String userInput = "获取项目 test 2026-01-01 00:00:00 到 2026-02-01 00:00:00 的数据，并进行分析处理";
String result = coordinatorAgent.processRequest(userInput);
System.out.println("分析结果：" + result);
```

### 2. 智能体调度器使用

```java
@Autowired
private AgentScheduler agentScheduler;

// 提交任务给智能体
String taskId = agentScheduler.submitTask("coordinator", "process_request", "分析项目 test 的数据安全风险");

// 获取任务状态和结果
AgentScheduler.TaskStatus status = agentScheduler.getTaskStatus(taskId);
String result = agentScheduler.getTaskResult(taskId);
```

### 3. 文档学习技能使用

```java
@Autowired
private SkillManager skillManager;

// 处理文档学习请求
DocumentLearningSkill skill = skillManager.getSkill("documentLearning", DocumentLearningSkill.class);
List<String> documentUrls = List.of(
    "https://example.com/api-documentation.html",
    "https://example.com/business-requirements.pdf"
);
String userRequirement = "基于这些文档，实现一个用户认证系统";
String result = skill.processRequest(documentUrls, userRequirement);
```

## API 文档

### 核心类

- **CoordinatorAgent**：协调多个智能体的执行
- **SequentialAgentExecutor**：顺序执行智能体，支持数据传递
- **AgentDataContext**：智能体间数据传递上下文
- **IntentRecognitionAgent**：识别用户查询意图
- **DynamicPromptAgent**：生成动态提示词
- **DataAnalysisAgent**：执行数据分析
- **MessageOptimizationAgent**：优化消息存储
- **AgentScheduler**：调度智能体任务
- **ContextManager**：管理上下文传递
- **ChatModelFactory**：统一管理 ChatModel 实例
- **DocumentLearningSkill**：文档学习技能

### 工具类

- **DataAnalysisTools**：提供数据分析功能
- **ShortTermMemoryManager**：管理短期记忆
- **LongTermMemoryManager**：管理长期记忆
- **PerformanceOptimizer**：性能优化
- **ErrorHandler**：错误处理

### 核心配置

- **ChatModelFactory**：统一管理 DashScope API 和 ChatModel，配置优先读取 `spring.ai.dashscope.api-key`，fallback 到环境变量 `AI_DASHSCOPE_API_KEY`

## 技术挑战与解决方案

1. **智能体协作**：使用 Spring AI Alibaba 的 Multi-agent 模式 + SequentialAgentExecutor 自定义实现
2. **上下文管理**：利用 Context Engineering 技术（ContextLoader、TokenBudgetManager、CheckpointRecoveryManager）
3. **记忆存储**：实现 MemoryStore 和相关工具
4. **性能优化**：ChatModelFactory 单例模式 + 智能体实例缓存
5. **数据传递**：使用 AgentDataContext + 占位符 `{user_intent}`、`{generated_prompt}` 实现智能体间的数据传递
6. **资源管理**：AgentScheduler 添加 shutdown 方法，正确释放线程池资源

## 后续优化方案

### 6.1 优先级排序

1. **P0 (高优先级)**
   - 支持更多 AI 模型：集成 OpenAI、Anthropic 等其他 LLM 提供商
   - 增加更多数据分析工具：扩展数据分析能力，支持更多数据格式和分析方法
   - 优化系统性能：进一步优化智能体实例缓存，减少响应时间

2. **P1 (中优先级)**
   - 实现更复杂的多智能体协作模式：支持更灵活的智能体编排
   - 添加监控和日志系统：实现系统运行状态的监控和日志记录
   - 增强安全性：加强 API 密钥管理和权限控制

3. **P2 (低优先级)**
   - 提供可视化界面：开发 Web 界面，方便用户使用和管理
   - 支持更多语言：添加多语言支持，适应国际化需求
   - 实现自动扩展：根据负载自动调整系统资源

### 6.2 具体优化建议

#### 支持更多 AI 模型
- **目标**：集成 OpenAI、Anthropic 等其他 LLM 提供商
- **实施步骤**：
  1. 创建统一的 LLM 接口
  2. 实现不同 LLM 提供商的适配器
  3. 配置模型选择机制，支持动态切换
- **预期效果**：提高系统的灵活性和适应性，用户可以根据需要选择不同的模型

#### 增加更多数据分析工具
- **目标**：扩展数据分析能力，支持更多数据格式和分析方法
- **实施步骤**：
  1. 分析常见的数据分析需求
  2. 开发相应的分析工具
  3. 集成到 DataAnalysisAgent 中
- **预期效果**：提高系统的分析能力，满足更多业务场景的需求

#### 优化系统性能
- **目标**：进一步优化智能体实例缓存，减少响应时间
- **实施步骤**：
  1. 分析系统性能瓶颈
  2. 优化缓存策略
  3. 实现异步处理机制
- **预期效果**：提高系统的响应速度和并发处理能力

#### 实现更复杂的多智能体协作模式
- **目标**：支持更灵活的智能体编排
- **实施步骤**：
  1. 研究不同的智能体协作模式
  2. 实现相应的编排机制
  3. 提供配置界面，方便用户定义协作流程
- **预期效果**：提高系统的灵活性和可扩展性，适应更复杂的业务场景

#### 添加监控和日志系统
- **目标**：实现系统运行状态的监控和日志记录
- **实施步骤**：
  1. 集成监控系统（如 Prometheus）
  2. 实现详细的日志记录
  3. 开发监控界面
- **预期效果**：提高系统的可观测性，便于问题定位和性能优化

## 贡献指南

欢迎提交 Issue 和 Pull Request 来帮助改进这个项目！

## 许可证

本项目采用 MIT 许可证。