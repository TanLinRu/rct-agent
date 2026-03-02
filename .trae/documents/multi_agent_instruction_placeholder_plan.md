# 多智能体Instruction占位符实现计划

## 项目背景
根据用户要求，需要使用传递Instruction占位符的方式实现多智能体编排，流程为：intentAgent生成意图信息 → 传递给prompt agent → prompt agent生成数据 → 传递给analysisAgent → analysisAgent完成数据分析。

## 任务分解与优先级

### [x] 任务1: 学习Instruction占位符机制
- **Priority**: P0
- **Depends On**: None
- **Description**:
  - 学习Instruction占位符的使用方法
  - 理解{input}、{outputKey}、{stateKey}等占位符的含义和使用场景
  - 掌握如何在多智能体系统中使用占位符传递数据
- **Success Criteria**:
  - 能够理解Instruction占位符的工作原理
  - 能够正确使用占位符在智能体之间传递数据
- **Test Requirements**:
  - `human-judgement` TR-1.1: 能够解释Instruction占位符的核心概念
  - `human-judgement` TR-1.2: 能够正确使用占位符传递数据
- **Notes**: 参考多智能体文档中的占位符机制部分

### [x] 任务2: 设计基于占位符的智能编排架构
- **Priority**: P0
- **Depends On**: 任务1
- **Description**:
  - 设计基于Instruction占位符的智能编排架构
  - 定义每个智能体的outputKey和输入格式
  - 设计智能体之间的数据传递流程
- **Success Criteria**:
  - 架构设计符合Instruction占位符的使用规范
  - 智能体之间的数据传递流程清晰
  - 占位符使用合理
- **Test Requirements**:
  - `human-judgement` TR-2.1: 架构设计清晰合理
  - `human-judgement` TR-2.2: 占位符使用符合最佳实践
- **Notes**: 确保每个智能体的outputKey与后续智能体的占位符对应

### [x] 任务3: 实现intentAgent
- **Priority**: P0
- **Depends On**: 任务2
- **Description**:
  - 实现意图识别智能体
  - 配置合适的outputKey，便于后续智能体引用
  - 确保意图信息能够正确传递
- **Success Criteria**:
  - intentAgent能够正确识别用户意图
  - 意图信息能够通过outputKey存储
  - 后续智能体能够通过占位符引用意图信息
- **Test Requirements**:
  - `programmatic` TR-3.1: 代码能够正常编译
  - `human-judgement` TR-3.2: 意图识别结果准确
- **Notes**: 使用有意义的outputKey，如"user_intent"

### [x] 任务4: 实现prompt agent
- **Priority**: P0
- **Depends On**: 任务3
- **Description**:
  - 实现动态提示词生成智能体
  - 使用{outputKey}占位符引用intentAgent的输出
  - 配置合适的outputKey，便于analysisAgent引用
- **Success Criteria**:
  - prompt agent能够正确使用占位符引用意图信息
  - 能够生成高质量的提示词
  - 提示词能够通过outputKey存储
- **Test Requirements**:
  - `programmatic` TR-4.1: 代码能够正常编译
  - `human-judgement` TR-4.2: 提示词质量高
- **Notes**: 使用{user_intent}占位符引用意图信息，outputKey设置为"generated_prompt"

### [x] 任务5: 实现analysisAgent
- **Priority**: P0
- **Depends On**: 任务4
- **Description**:
  - 实现数据分析智能体
  - 使用{outputKey}占位符引用prompt agent的输出
  - 完成数据分析任务
- **Success Criteria**:
  - analysisAgent能够正确使用占位符引用提示词
  - 能够完成数据分析任务
  - 分析结果准确
- **Test Requirements**:
  - `programmatic` TR-5.1: 代码能够正常编译
  - `human-judgement` TR-5.2: 分析结果准确
- **Notes**: 使用{generated_prompt}占位符引用提示词

### [ ] 任务6: 实现顺序执行的多智能体编排
- **Priority**: P0
- **Depends On**: 任务3, 任务4, 任务5
- **Description**:
  - 实现顺序执行的多智能体编排
  - 确保智能体按照intentAgent → prompt agent → analysisAgent的顺序执行
  - 验证数据传递是否正确
- **Success Criteria**:
  - 智能体能够按照正确的顺序执行
  - 数据能够通过占位符正确传递
  - 整个编排流程能够正常完成
- **Test Requirements**:
  - `programmatic` TR-6.1: 代码能够正常编译
  - `programmatic` TR-6.2: 编排流程能够正常执行
- **Notes**: 使用SequentialAgent或类似机制实现顺序执行

### [ ] 任务7: 测试多智能体编排流程
- **Priority**: P1
- **Depends On**: 任务6
- **Description**:
  - 测试整个多智能体编排流程
  - 验证智能体之间的数据传递是否正确
  - 验证分析结果是否准确
- **Success Criteria**:
  - 编排流程能够处理各种输入场景
  - 数据传递正确
  - 分析结果准确
- **Test Requirements**:
  - `programmatic` TR-7.1: 测试用例执行成功
  - `human-judgement` TR-7.2: 测试覆盖各种场景
- **Notes**: 编写测试用例，覆盖正常和异常场景

### [ ] 任务8: 优化智能编排性能
- **Priority**: P2
- **Depends On**: 任务7
- **Description**:
  - 优化智能编排的性能和可靠性
  - 优化智能体的创建和执行过程
  - 提高系统的稳定性和响应速度
- **Success Criteria**:
  - 智能编排流程执行效率高
  - 系统稳定性好
  - 响应速度快
- **Test Requirements**:
  - `programmatic` TR-8.1: 执行时间在合理范围内
  - `human-judgement` TR-8.2: 系统运行稳定
- **Notes**: 考虑智能体的缓存和复用

## 实现策略

1. **学习先行**：先深入学习Instruction占位符的使用方法
2. **架构设计**：基于占位符机制设计智能编排架构
3. **逐个实现**：按照intentAgent → prompt agent → analysisAgent的顺序实现各个智能体
4. **集成测试**：实现顺序执行的多智能体编排并测试
5. **性能优化**：优化智能编排的性能和可靠性

## 技术参考
- 多智能体文档：https://java2ai.com/docs/frameworks/agent-framework/advanced/multi-agent
- 重点参考Instruction占位符机制和顺序执行的多智能体模式
