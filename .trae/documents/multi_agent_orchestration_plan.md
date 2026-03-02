# Multi-Agent 编排实现计划

## 项目背景
根据用户要求，需要参考Multi-agent模式，编排IntentRecognitionAgent、DynamicPromptAgent和DataAnalysisAgent，不需要把agent作为工具注入。

## 任务分解与优先级

### [x] 任务1: 修改CoordinatorAgent，移除AgentTool模式
- **Priority**: P0
- **Depends On**: None
- **Description**:
  - 修改CoordinatorAgent.java文件，移除AgentTool的使用
  - 改为直接编排三个智能体的顺序执行
- **Success Criteria**:
  - CoordinatorAgent不再使用AgentTool.getFunctionToolCallback()方法
  - 代码能够正常编译
- **Test Requirements**:
  - `programmatic` TR-1.1: 代码编译成功
  - `human-judgement` TR-1.2: 代码结构清晰，符合Multi-agent模式
- **Notes**: 参考Multi-agent模式中的顺序执行方式

### [x] 任务2: 实现智能体顺序编排逻辑
- **Priority**: P0
- **Depends On**: 任务1
- **Description**:
  - 实现顺序编排流程：IntentRecognitionAgent → DynamicPromptAgent → DataAnalysisAgent
  - 确保每个智能体的输出能够正确传递给下一个智能体
- **Success Criteria**:
  - 编排流程能够正常执行
  - 数据能够在智能体之间正确传递
- **Test Requirements**:
  - `programmatic` TR-2.1: 编排流程执行成功
  - `human-judgement` TR-2.2: 执行流程逻辑清晰
- **Notes**: 使用结构化的方式传递数据，确保每个智能体都能接收到正确的输入

### [x] 任务3: 优化错误处理和异常管理
- **Priority**: P1
- **Depends On**: 任务2
- **Description**:
  - 为编排流程添加错误处理机制
  - 确保任何智能体的失败都能被正确捕获和处理
- **Success Criteria**:
  - 当某个智能体失败时，流程能够优雅处理
  - 错误信息能够清晰地返回给用户
- **Test Requirements**:
  - `programmatic` TR-3.1: 错误处理能够正确捕获异常
  - `human-judgement` TR-3.2: 错误信息清晰易懂
- **Notes**: 参考现有智能体的错误处理方式

### [x] 任务4: 测试多智能体编排流程
- **Priority**: P1
- **Depends On**: 任务3
- **Description**:
  - 测试整个多智能体编排流程
  - 验证数据在智能体之间的传递是否正确
  - 验证错误处理是否有效
- **Success Criteria**:
  - 编排流程能够处理各种输入场景
  - 测试用例能够通过
- **Test Requirements**:
  - `programmatic` TR-4.1: 测试用例执行成功
  - `human-judgement` TR-4.2: 测试覆盖各种场景
- **Notes**: 编写测试用例，覆盖正常和异常场景

### [ ] 任务5: 优化性能和可靠性
- **Priority**: P2
- **Depends On**: 任务4
- **Description**:
  - 优化智能体的创建和执行过程
  - 提高编排流程的可靠性和性能
- **Success Criteria**:
  - 编排流程执行效率高
  - 系统稳定性好
- **Test Requirements**:
  - `programmatic` TR-5.1: 执行时间在合理范围内
  - `human-judgement` TR-5.2: 系统运行稳定
- **Notes**: 考虑智能体的缓存和复用

## 实现策略

1. **顺序编排模式**：采用线性顺序执行，确保每个智能体的输出作为下一个智能体的输入
2. **数据传递**：使用结构化的方式传递数据，确保每个智能体都能接收到正确的输入
3. **错误处理**：为每个智能体的执行添加异常处理，确保流程的健壮性
4. **测试验证**：编写测试用例，验证整个编排流程的正确性

## 技术参考
- Multi-agent模式：https://java2ai.com/docs/frameworks/agent-framework/advanced/multi-agent
- 重点参考顺序执行和数据传递的最佳实践
