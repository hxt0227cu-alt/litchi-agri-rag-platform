# Known Limitations

## 2026-09-04 status

- 100 并发聊天测试已修复并通过：根因是 `EvaluationService.getActiveFeedbackRules()` 在 TTL 过期瞬间所有并发请求同时全量重算全部历史记录的 rubric 评分（惊群），单线程 ~3-4s 的构建在 100 线程 CPU 争抢下放大到 57-98s/请求，全部超过客户端 15s 超时。修复为后台线程每 TTL/2 周期预热缓存 + 双重检查锁兜底，请求热路径只读缓存。修复后 100 并发 200 请求 100% 成功、P50 12.6ms、P95 69.7ms、max 75ms，三次复测稳定；原始失败报告保留于 `reports/validation/20260731-162446/`。
- 并发链路依赖全部不可用（Ollama/Milvus/Neo4j 均无监听），已通过启动预探测自动熔断避免首波真实连接挂起；其中 Neo4j 首个请求真实连接 127.0.0.1:7687 曾挂起约 2.7 分钟并 synchronized 串行阻塞整批并发。当前 100 并发结果代表"全降级 + 真实并发"能力，真实 LLM 参与时的吞吐仍需外部环境复测。
- MySQL 已通过 WSL2 Docker（MySQL 8.0.46 容器 `litchi-mysql`）运行，但 WSL localhost 转发间歇抖动会导致 3306 间歇不可达；已新增后台重连器（15s 周期）+ ChatHistory 对账机制，启动失败或运行中断不再永久降级（实测多次自动重连并补齐本地缺口）。
- Agent 运行持久化已完成全量拆分：主表 `platform_agent_runs` 结构化列 + 步骤表 `platform_agent_steps` + 审批表 `platform_agent_approvals`，并实现跨实例恢复（60s 周期扫描，超过 120s 未推进的非终态运行标记 `interrupted`；等待审批运行跨实例保留可继续决策）。写工具幂等已实现：Agent 审批写工具以运行 ID 为 `idempotencyKey`，`createPlan` 同键重复提交返回既有方案（synchronized 查重 + 幂等键持久化），并有单元测试覆盖重复审批/重复恢复场景。剩余限制：恢复采用"安全落地"语义——不自动续跑，超时阈值 120s 为启发式常量（可用 `app.agent.recover-stale-seconds` 调整）；MySQL 投影采用全量同步，未叠唯一索引，若未来改为 MySQL 主存储可加唯一索引形成双层防线。
- 数据平台和可观测目录属于可部署骨架，不等同于已验证的生产高可用平台。
- 模型不可用时 Planner 和 Synthesizer 会明确降级；降级输出不能作为真实模型质量结果。Policy Guard 为确定性规则拦截，可在降级模式下完整执行安全评测，但真实模型下的提示注入防御仍需额外验证。
- 前端生产构建仍有 Element Plus 全量引入导致的超 600kB chunk 警告；Three.js 已单独分包（`vendor-three`）。后续可引入 `unplugin-vue-components` 做组件按需加载（依赖离线环境可用的 npm 网络）。
- 当前演示账号密码只适合本机，生产部署必须更换并使用 Secret 管理。
- 尚未开展真实用户研究；任何任务完成率、SUS、满意度和闭环率只能在完成研究后引用。
- 真实模型检索评测（BGE-M3+reranker）已完成，Recall@5 提升至 100%（30/30）但 MRR 从本地基线 0.850 降至 0.778——语义检索找回全部正确证据，但 reranker 将部分正确证据排在 rank 3-5，排序需调优；该评测基于 SiliconFlow 免费 API，latency avg 739ms 含 API 往返，不代表本地部署性能。端到端引用准确率与问答质量仍未评测。

## 2026-07-31 verified limitations

1. 100 并发聊天测试未通过：200 个请求仅 38 个成功，P95 为 15.18 秒。当前判断仍存在同步持久化或请求线程占用瓶颈；已停止继续调优并保留原始报告。
2. Docker 当前不可用，Compose、Kafka/ClickHouse、Prometheus/Grafana 和 Helm 未在本轮执行验证。
3. Agent 运行使用单表序列化快照并带内存回退，尚未拆分为独立 step、tool call 和 approval 表，也未完成跨实例抢占恢复。
4. 数据平台和可观测目录属于可部署骨架，不等同于已验证的生产高可用平台。
5. 模型不可用时 Planner 和 Synthesizer 会明确降级；降级输出不能作为真实模型质量结果。
6. 前端生产构建存在两个超过 600kB 的 chunk 警告，Three.js 和 Element Plus 仍需进一步按需加载。
7. 当前演示账号密码只适合本机，生产部署必须更换并使用 Secret 管理。
8. 尚未开展真实用户研究；任何任务完成率、SUS、满意度和闭环率只能在完成研究后引用。

## Capacity test incident

详细过程见 [20260731 负载测试记录](202607worklog/04-incidents/20260731-load-test-limit.md)。
