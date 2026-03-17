# Plan Mode - Task Todo

说明：按优先级划分任务，状态字段取值：pending、in_progress、completed、blocked。

- [x] 1. 更新 README.md 文档
  - 说明：文档中已更新 JDK 路径、API Key 配置、核心类变更等
  - 状态：completed
  - 备注：覆盖了 JDK21 路径、构建/运行说明、核心组件新增等信息

- [x] 2. 清理 docs 目录过时文档
  - 说明：移除多余/过时的占位文档，保留核心设计文档
  - 状态：completed
  - 备注：清理了 TODO_ITEMS.md、multi_agent_instruction_placeholder 等冗余内容

- [x] 3. 上传代码到 git
  - 说明：提交并推送到远端主分支
  - 状态：completed
  - 备注：包含 README 更新、核心实现改动及 .gitignore 的添加

- [x] 4. 引入并使用 ChatModelFactory（Centralized ChatModel）
  - 说明：统一管理 DashScope APIKey 与 DashScopeChatModel
  - 状态：completed
  - 备注：实现了单例工厂，避免重复实例化

- [x] 5. 新增 AgentDataContext（智能体间数据上下文）
  - 说明：用于跨智能体的数据传递与占位符替换
  - 状态：completed
  - 备注：支持占位符替换，例如 {user_intent}、{generated_prompt} 等

- [x] 6. 新增 SequentialAgentExecutor（顺序执行器）
  - 说明：统一执行顺序智能体链，并收集各阶段输出
  - 状态：completed
  - 备注：支持按顺序调用并把输出注入下一步

- [x] 7. 修改 CoordinatorAgent，使用新的编排方式
  - 说明：替换原有手动顺序，改为通过 SequentialAgentExecutor 协调
  - 状态：completed
  - 备注：提高可维护性与扩展性

- [x] 8. 优化 SkillManager 的类型安全
  - 说明：改用泛型、并发安全的实现，增加取消/清空方法
  - 状态：completed
  - 备注：提升可维护性与鲁棒性

- [ ] 9. 增加端到端测试用例（E2E）
  - 说明：覆盖从意图识别到数据分析的完整流程，以及数据传递正确性
  - 状态：pending
  - 备注：待你确认引入测试框架后实现

- [ ] 10. 引入多模型提供商适配层（ModelProvider）
  - 说明：支持 OpenAI/Anthropic 等模型提供商的切换
  - 状态：pending
  - 备注：提升容错与成本管理能力

- [ ] 11. 观测与治理完善（Telemetry & 监控）
  - 说明：接入 OpenTelemetry / Prometheus，追踪执行路径与关键指标
  - 状态：pending
  - 备注：便于运维与稳定性提升

- [ ] 12. 安全与合规模块初步强化
  - 说明：密钥管理、审计日志、数据脱敏策略初步实现
  - 状态：pending
  - 备注：结合组织需求逐步实现完整治理

- [ ] 13. 文档与对外 API 向导完善
  - 说明：更新 API 文档、示例、CLI/SDK 草案
  - 状态：pending
  - 备注：便于团队协作与对外演示

后续计划与落地建议
- 阶段入口（1–2 周内）
  - 将任务 9 与 10 置为 in_progress，尽快实现端到端测试与多模型适配的雏形。
  - 将任务 11（观测与治理）列为 in_progress，先集成 OpenTelemetry 基本追踪。
- 中期（2–8 周）
  - 完成任务 9、10、11 的落地，并对接 CI/CD 流水线，增加自动化测试与构建检查。
  - 做好安全与合规的检查清单，形成可复用的治理模板。
- 长期（2–3 个月及以上）
  - 架构进入分布式/插件化阶段，增加消息队列、分布式调用、分区存储等能力。
  - 完善知识管理、知识图谱、跨语言支持等未来扩展方向。

你可以执行的下一步
- 让我将以上内容生成实际的 task_todo.md 文件，并提交一个初步的 PR/分支草稿，附带一个简单的里程碑甘特图或看板（如 GitHub Projects/Issue），以便团队跟踪。
- 或者你给我明确的优先级和时间线，我可以把上面的清单再细化成更具体的任务条目和验收标准。
