# AI Agent 架构设计

## 1. 定位

任务智能体用于处理需要多类证据的荔枝农技任务。它与普通问答的区别是：普通问答固定执行 RAG 链路，任务智能体先生成受限计划，再按顺序调用工具，最后基于工具结果综合回答并返回完整执行轨迹。

当前版本采用 Planner -> Guard -> Executor -> Synthesizer 流程：

1. Planner 根据任务和当前角色可用工具生成 JSON 计划。
2. Guard 丢弃未知、重复、越权和超过步骤预算的工具。
3. Executor 顺序调用服务端注册的只读工具，记录状态和耗时。
4. Synthesizer 只能依据工具结果生成结论；模型不可用时返回明确的降级结果。

模型通过统一的 `LLMService` Gateway 访问：`APP_LLM_PROVIDER=ollama` 使用本机 `/api/chat`，`APP_LLM_PROVIDER=vllm` 或 `openai-compatible` 使用 `/v1/chat/completions`。业务 Agent 不感知具体推理引擎，切换云端 GPU 只改变环境配置。

## 2. 工具与权限

| 工具 | 能力 | 允许角色 | 副作用 |
| --- | --- | --- | --- |
| `knowledge_search` | 检索知识文档片段 | 全部已登录角色 | 无 |
| `knowledge_graph` | 查询品种、病虫害、药剂和技术关系 | 全部已登录角色 | 无 |
| `plan_recommendation` | 推荐门店解决方案 | 农户、技术员 | 无 |
| `pending_remedy_plan` | 生成待审核处置方案并在审批后保存 | 技术员 | 有，必须审批 |

Agent 不允许执行任意 HTTP、Shell 或数据库语句。`pending_remedy_plan` 是当前唯一写工具：执行阶段只生成动作预览并进入 `waiting_approval`，技术员批准后才调用现有方案服务保存为未启用方案；拒绝审批不会写入。其他写操作仍禁止复用只读执行路径直接落库。

## 3. 接口

### 创建运行

`POST /api/agents/runs`

```json
{
  "goal": "连续降雨后叶片出现褐色病斑，请综合研判并给出处理顺序",
  "sessionId": "agent-demo-001",
  "maxSteps": 3
}
```

响应包含 `runId`、`status`、`degraded`、总耗时、执行步骤、工具结果、最终答案和规划模式。`maxSteps` 的硬上限为 4。

响应同时包含 `riskLevel`、`reviewRequired`、`checkpoint` 和可选的 `pendingAction`。涉及写工具时，系统标记高风险并等待技术员明确批准；每个工具步骤完成后更新 checkpoint。

### 查询运行

`GET /api/agents/runs/{runId}`

运行记录按创建用户隔离。当前使用最多 500 条的进程内存存储，并在 MySQL 可用时写入 `platform_agent_runs`；MySQL 不可用时回退内存以保持演示可用。checkpoint 已记录工作流版本、当前步骤、计划工具、完成工具和待审批工具。进程重启后的自动扫描恢复、独立步骤表和审批表仍属于下一阶段，不能描述为已经完成。

## 4. 可观测性设计

下一阶段为 Agent 暴露以下指标：

- `agent_runs_total{status,planner_mode,degraded}`
- `agent_run_duration_seconds`
- `agent_tool_calls_total{tool,status}`
- `agent_tool_duration_seconds{tool}`
- `agent_steps_per_run`
- `agent_budget_rejections_total{reason}`
- `agent_risk_reviews_total{risk_level,decision}`

日志仅记录 `runId`、用户 ID、角色、工具名、状态和耗时，不记录完整问题、工具证据或模型提示词。工具名来自固定枚举，避免 Prometheus 高基数标签。

## 5. 评测方法

- 建立任务级数据集，分别覆盖单工具、多工具、无证据、模型不可用、工具失败和越权诱导。
- 记录规划工具准确率、非法工具拒绝率、任务完成率、证据引用正确率、平均步骤数、P95 总耗时和单任务模型成本。
- 对比固定 RAG 与 Agent：只有当多步骤任务的完成率或答案质量显著提高时才走 Agent，简单问题继续使用普通问答，避免无意义增加延迟和成本。
