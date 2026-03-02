---
name: document-learning
version: "1.0.0"
description: 基于用户提供的文档链接进行学习，理解用户需求，生成需求文档，获取用户确认，规划需求实现流程，并进行文档化的完整过程
bonded_agent: default
bond_type: PRIMARY_BOND
allowed-tools: Read, Write, Bash, Glob, Grep

# Parameter Validation
parameters:
  document_urls:
    type: array
    items:
      type: string
    description: 包含用户提供的文档链接列表
  user_requirement:
    type: string
    description: 用户提出的初步需求描述
  document_types:
    type: array
    items:
      type: string
    description: 指定文档类型（如"html", "pdf", "docx"等）
  priority:
    type: string
    enum: [high, medium, low]
    default: medium
    description: 需求优先级
  deadline:
    type: string
    format: date-time
    description: 需求截止日期（ISO格式）
---

# 文档学习与需求实现

基于用户提供的文档链接进行学习，理解用户需求，生成需求文档，获取用户确认，规划需求实现流程，并进行文档化的完整过程。

## Overview

此技能提供了一个完整的流程，用于从用户提供的文档中学习知识，然后基于这些知识实现用户的需求。整个流程包括文档学习、需求分析、需求文档生成、用户确认、实现规划、需求实现和文档化沉淀等步骤。

## When to Use This Skill

使用此技能当你需要：
- 基于多个文档链接学习知识
- 分析和理解复杂的用户需求
- 生成结构化的需求文档
- 规划需求实现的流程
- 对整个过程进行文档化

## Execution Flow

1. **文档学习**：接收用户提供的文档链接，读取并处理文档内容，提取核心信息和知识，构建知识模型。

2. **需求分析**：分析用户提出的初步需求，将需求与已学习的文档内容进行关联，识别核心要点和潜在约束，发现模糊点和需要澄清的地方。

3. **需求文档生成**：基于分析结果生成详细的需求文档，确保文档的完整性和一致性，明确需求的边界和范围，提供具体的验收标准。

4. **用户确认**：向用户呈现生成的需求文档，解释文档中的关键内容和决策，收集用户的反馈和修改意见，根据用户反馈修订需求文档，获得用户对最终需求文档的确认。

5. **实现规划**：基于确认的需求文档规划实现流程，分解需求为可执行的任务，确定任务的优先级和依赖关系，估计实现所需的资源和时间，制定详细的实施计划。

6. **需求实现**：按照规划的流程执行需求实现，基于学习到的知识解决实现过程中的问题，确保实现结果符合需求文档的要求，进行必要的调整和优化。

7. **文档化沉淀**：记录整个实现过程和结果，整理学习到的知识和经验，生成实现文档和使用指南，建立知识索引，方便后续查询和使用。

## Output Format

### 成功响应
```json
{
  "status": "success",
  "result": {
    "phase": "completed",
    "documents_learned": ["url1", "url2"],
    "requirement_document": "<需求文档内容>",
    "implementation_plan": "<实现规划内容>",
    "deliverables": "<交付物描述>",
    "knowledge_assets": "<知识资产存储位置>"
  }
}
```

### 中间响应
```json
{
  "status": "in_progress",
  "result": {
    "phase": "<当前阶段>",
    "message": "<阶段进度信息>",
    "next_steps": "<下一步操作>"
  }
}
```

### 错误响应
```json
{
  "status": "error",
  "result": {
    "error_code": "<错误代码>",
    "error_message": "<错误描述>",
    "suggestion": "<解决建议>"
  }
}
```

## Example Usage

### 基本用法
```json
{
  "document_urls": [
    "https://example.com/api-documentation.html",
    "https://example.com/business-requirements.pdf"
  ],
  "user_requirement": "基于提供的文档，实现一个用户认证系统"
}
```

### 高级用法
```json
{
  "document_urls": [
    "https://example.com/architecture-design.md",
    "https://example.com/technical-specification.docx"
  ],
  "user_requirement": "根据架构设计和技术规范，实现一个微服务架构的订单管理系统",
  "document_types": ["markdown", "docx"],
  "priority": "high",
  "deadline": "2026-04-01T00:00:00Z"
}
```

## Error Handling

### 常见错误
- **DOCUMENT_FETCH_ERROR**: 文档获取失败
- **DOCUMENT_PARSE_ERROR**: 文档解析失败
- **REQUIREMENT_ANALYSIS_ERROR**: 需求分析失败
- **USER_CONFIRMATION_FAILED**: 用户确认失败
- **IMPLEMENTATION_ERROR**: 实现过程出错

### 错误处理策略
- 提供清晰的错误信息
- 给出具体的解决建议
- 在适当情况下自动重试
- 支持用户手动干预和调整

## Limitations and Notes

### 文档限制
- 支持的文档格式：HTML、PDF、Office文档、Markdown
- 单个文档大小限制：10MB
- 文档链接数量限制：最多10个

### 需求限制
- 需求描述应清晰明确
- 需求应与提供的文档相关
- 复杂需求可能需要分阶段实现

### 性能限制
- 文档学习时间取决于文档大小和数量
- 复杂需求的分析和规划可能需要较长时间
- 实现过程的时间取决于需求的复杂度