# Context 设计审查报告

## 一、当前 Context 架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                     改进后的 Context 设计                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │               ContextManager (整合改进)                      │ │
│  │  ┌─────────────┐  ┌────────────┐  ┌───────────────────┐  │ │
│  │  │ ContextLoader│  │ TokenBudget│  │ CheckpointRecovery │  │ │
│  │  │ (L1/L2/L3)  │  │ Manager    │  │ Manager            │  │ │
│  │  └─────────────┘  └────────────┘  └───────────────────┘  │ │
│  │  ┌─────────────────┐  ┌─────────────────────────────┐    │ │
│  │  │ PromptVersion    │  │ MessagePurifier             │    │ │
│  │  │ Manager         │  │ (脱敏/净化)                  │    │ │
│  │  └─────────────────┘  └─────────────────────────────┘    │ │
│  └───────────────────────────────────────────────────────────┘ │
│                              │                                  │
│         ┌───────────────────┼───────────────────┐             │
│         ▼                   ▼                   ▼             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐       │
│  │  Redis      │    │  H2 Database │    │  MCP/Nacos  │       │
│  │  (L1 Hot)   │    │  (L2 Warm)   │    │  服务发现   │       │
│  │  最近N轮    │    │  会话摘要    │    │  熔断恢复   │       │
│  └─────────────┘    └─────────────┘    └─────────────┘       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 二、已实现的改进

### 2.1 分层上下文加载 ✅

| 层级 | 存储 | 功能 |
|------|------|------|
| L1 (Hot) | H2 | 最近N轮完整对话 |
| L2 (Warm) | H2 | 会话摘要 |
| L3 (Profile) | H2 | 用户画像标签 |

**实现类:** `ContextLoader`

### 2.2 Token 预算管理 ✅

| 功能 | 描述 |
|------|------|
| 预算计算 | 实时监控Input+MaxOutput Tokens |
| 滑动窗口 | 保留最近N轮对话 |
| 关键帧 | 保留System Prompt + Tool Call结果 |
| 语义摘要 | 中间历史替换为语义摘要 |

**实现类:** `TokenBudgetManager`

### 2.3 断点恢复机制 ✅

| 功能 | 描述 |
|------|------|
| 状态持久化 | MCP调用前保存Checkpoint到H2 |
| 中断检测 | 自动检测ABORTED状态会话 |
| 状态恢复 | 从Checkpoint恢复现场 |
| 跳过优化 | 已成功的Tool调用可跳过 |

**实现类:** `CheckpointRecoveryManager`

### 2.4 Prompt版本管理 ✅

| 功能 | 描述 |
|------|------|
| 版本控制 | 每个Agent Type独立版本管理 |
| 语义校验 | 压缩后约束条件校验 |
| 历史记录 | 版本变更可追溯 |

**实现类:** `PromptVersionManager`

### 2.5 消息净化 ✅

| 功能 | 描述 |
|------|------|
| Thought剥离 | Agent间传递剥离内部思考过程 |
| PII脱敏 | 邮箱/手机号/身份证脱敏 |
| 注入检测 | Prompt Injection防护 |

**实现类:** `MessagePurifier`

## 三、新增文件清单

### Context核心
- `ContextLoader.java` - 分层上下文加载器
- `TokenBudgetManager.java` - Token预算压缩
- `CheckpointRecoveryManager.java` - 断点恢复管理
- `PromptVersionManager.java` - Prompt版本管理
- `MessagePurifier.java` - 消息净化

### 数据层增强
- `ChatMessageService` - 新增getLatestCheckpointBySessionId方法
- `ContextManager` - 集成所有新组件

### 测试
- `ContextDesignImprovementsTest.java` - 6个测试场景

## 四、测试场景

1. **testLayeredContextLoading** - 分层上下文加载
2. **testTokenBudgetCompression** - Token预算压缩
3. **testCheckpointRecovery** - 断点恢复
4. **testPromptVersionManagement** - Prompt版本管理
5. **testMessagePurifier** - 消息净化
6. **testFullContextFlow** - 完整流程测试
