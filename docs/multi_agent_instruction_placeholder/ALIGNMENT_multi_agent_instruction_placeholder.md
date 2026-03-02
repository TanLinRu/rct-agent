# ALIGNMENT: 基于Instruction占位符的多智能体编排系统

## 项目上下文分析

### 现有项目结构
- 项目基于Spring AI Alibaba 1.1.2.0实现
- 使用ReactAgent作为智能体实现基础
- 现有智能体包括：
  - IntentRecognitionAgent: 用户查询意图识别
  - DynamicPromptAgent: 动态提示词生成
  - DataAnalysisAgent: 数据分析
  - MessageOptimizationAgent: 消息优化
  - CoordinatorAgent: 协调智能体

### 现有技术栈
- Spring Boot
- Spring AI Alibaba 1.1.2.0
- DashScope (Alibaba Cloud) 作为LLM提供商
- ReactAgent 智能体框架

### 现有代码模式
- 每个智能体独立实现，通过CoordinatorAgent手动协调
- 智能体使用ReactAgent.builder()创建
- 已添加outputKey和instruction占位符配置

## 需求理解确认

### 原始需求
> 这里更倾向使用传递Instruction 占位符的方式，先是intentAgent生成后，传递意图信息给prompt agent，然后再把prompt agent生成的数据传递给 analysisAgent，这样去完成一个multi agent的业务逻辑

### 边界确认
- 任务范围：实现基于Instruction占位符的多智能体编排系统
- 技术约束：必须使用Spring AI Alibaba 1.1.2.0的ReactAgent
- 集成方案：利用现有的智能体结构，修改协调机制

### 需求理解
- 智能体执行顺序：IntentRecognitionAgent → DynamicPromptAgent → DataAnalysisAgent
- 数据传递方式：使用Instruction占位符进行智能体间数据传递
- 技术实现：利用Spring AI Alibaba的Multi-agent机制，而非手动顺序执行

### 疑问澄清
1. **技术实现方式**：Spring AI Alibaba 1.1.2.0是否提供SequentialAgent或类似的多智能体编排机制？
2. **占位符传递机制**：ReactAgent的instruction占位符如何与outputKey配合使用？
3. **智能体状态管理**：智能体间的状态如何正确传递和管理？