# CONSENSUS: 基于Instruction占位符的多智能体编排系统

## 明确的需求描述

### 核心需求
- 实现基于Instruction占位符的多智能体编排系统
- 智能体执行顺序：IntentRecognitionAgent → DynamicPromptAgent → DataAnalysisAgent
- 使用Instruction占位符进行智能体间数据传递
- 确保数据在智能体间正确流转

### 验收标准
1. 智能体能够按照指定顺序执行
2. 意图信息能够从IntentRecognitionAgent传递到DynamicPromptAgent
3. 生成的提示词能够从DynamicPromptAgent传递到DataAnalysisAgent
4. 整个流程能够正常完成，返回有效的分析结果
5. 系统具有良好的错误处理能力

## 技术实现方案

### 技术选择
- **基础框架**：Spring AI Alibaba 1.1.2.0
- **智能体实现**：ReactAgent
- **数据传递**：Instruction占位符 + outputKey
- **编排方式**：手动顺序执行（由于Spring AI Alibaba 1.1.2.0可能未提供SequentialAgent）

### 技术约束
- 必须使用Spring AI Alibaba 1.1.2.0的API
- 保持与现有项目结构的一致性
- 确保代码风格和质量标准

### 集成方案
1. **智能体配置**：
   - IntentRecognitionAgent：设置outputKey("user_intent")
   - DynamicPromptAgent：设置instruction("用户意图：{user_intent}\n请根据用户意图生成一个优化的提示词。")和outputKey("generated_prompt")
   - DataAnalysisAgent：设置instruction("提示词：{generated_prompt}\n请根据提示词执行数据分析任务。")

2. **协调机制**：
   - 修改CoordinatorAgent，实现智能体间的顺序执行
   - 确保数据通过占位符正确传递
   - 添加错误处理和日志记录

3. **状态管理**：
   - 利用ReactAgent的内置状态管理机制
   - 使用MemorySaver保存智能体状态

## 任务边界限制

### 范围限定
- 仅实现智能体编排和数据传递功能
- 不修改现有的智能体核心逻辑
- 不涉及前端界面和用户交互

### 技术限制
- 基于Spring AI Alibaba 1.1.2.0的API
- 使用ReactAgent作为智能体实现
- 手动顺序执行代替SequentialAgent

## 确认的关键假设

1. **API可用性**：Spring AI Alibaba 1.1.2.0的ReactAgent支持outputKey和instruction占位符功能
2. **数据传递**：通过手动顺序执行和正确的占位符配置，可以实现智能体间的数据传递
3. **错误处理**：系统能够处理智能体执行过程中的异常情况
4. **性能**：手动顺序执行的性能满足需求

## 技术实现细节

### 智能体配置
- **IntentRecognitionAgent**：
  - systemPrompt：专业的意图识别专家
  - outputKey："user_intent"

- **DynamicPromptAgent**：
  - systemPrompt：专业的提示词工程师
  - instruction："用户意图：{user_intent}\n请根据用户意图生成一个优化的提示词。"
  - outputKey："generated_prompt"

- **DataAnalysisAgent**：
  - systemPrompt：资深的数据安全分析专家
  - instruction："提示词：{generated_prompt}\n请根据提示词执行数据分析任务。"
  - 集成DataAnalysisTools工具

### 协调机制
- **CoordinatorAgent**：
  - 按顺序调用三个智能体
  - 传递前一个智能体的输出作为后一个智能体的输入
  - 处理异常情况
  - 记录执行日志