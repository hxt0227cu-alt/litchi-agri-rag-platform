# Evidence Index

本文件把仓库能力、测试方法和量化结果绑定到可复现证据。所有“通过”均来自真实命令；目标值与未执行实验不会写成实测结果。

## 测试环境

| 项目 | 配置 |
| --- | --- |
| 日期 | 2026-09-04 |
| OS | Windows 11 10.0.26200 |
| CPU | Intel Core i5-1240P，16 logical processors |
| 内存 | 15.7 GB |
| Java / Maven | OpenJDK 17.0.19 / Maven 3.9.11 |
| Node / Python | Node 22.23.1 / Python 3.11.9 |
| k6 | v1.2.3（本机运行真实负载测试） |
| Docker / GPU | WSL2 Ubuntu-24.04 + Docker 29.6.2（MySQL 8.0.46 容器 `litchi-mysql` 可运行）；本机无 GPU，真实模型评测待外部 API key |

## 证据矩阵

| 能力声明 | 命令或方法 | 实际结果 | 证据 |
| --- | --- | --- | --- |
| 后端测试 | `cd backend; mvn -B test` | 49 tests，0 failures/errors/skips | [本地验证报告](reports/verification/20260731-local-verification.md) |
| 前端权限 | `cd frontend; npm test` | passed | [本地验证报告](reports/verification/20260731-local-verification.md) |
| 前端构建 | `npm run build` | 1,729 modules，24.96s | [本地验证报告](reports/verification/20260731-local-verification.md) |
| Agent 任务集 | `python benchmarks/evaluate_agent.py --validate-only` | 60 条，`valid=true` | [任务集](datasets/evaluation/agent_tasks.jsonl) |
| 完整评测 | `python benchmarks/run_agent_evaluation.py` | 60/60 通过：RAG 30/30、Agent 20/20、Safety 10/10 | [评测结果](reports/evaluation/latest.jsonl) |
| 安全护栏（Policy Guard） | 10 条安全任务（提示注入/越权写/数据外泄等） | 10/10 拒绝，均返回 `refused` 且不执行任何工具 | [评测结果](reports/evaluation/latest.jsonl) / [PolicyGuardTest](backend/src/test/java/com/litchi/agent/PolicyGuardTest.java) |
| 混合检索（BM25+向量+Rerank） | 对比改进前后 `latest.jsonl` 与 `latest-hybrid.jsonl` | RAG Recall@top-4 由 24/30 提升至 30/30 | [评测结果](reports/evaluation/latest.jsonl) |
| Agent 负载（k6） | `k6 run benchmarks/agent-load.js --env RATE=2 --env DURATION=60s` | 120/120 完成，成功率 100%，Agent P95=508ms（阈值 12s） | [负载脚本](benchmarks/agent-load.js) |
| Agent 负载（k6 更高到达率） | `k6 run benchmarks/agent-load.js --env RATE=5` | 吞吐提升仍满足 P95 < 12s 与成功率 ≥ 99.5% | [负载脚本](benchmarks/agent-load.js) |
| 降级基线 | `measure-api-baseline.ps1` | Chat avg 159.88ms；Diagnosis avg 47.43ms | [报告](reports/validation/20260731-160911/baseline-report.md) / [JSON](reports/validation/20260731-160911/baseline-report.json) |
| 100 并发聊天（修复前） | `python benchmarks/concurrency_chat_test.py --concurrency 100 --total 200 --timeout 15` | 0%（200 全超时；服务端实际 57-98s/请求） | [本轮调优报告](reports/validation/20260904-concurrency-fix/agent-performance-report.md) |
| 100 并发聊天（修复后） | 同上 | **100%（200/200）**，P50 12.6ms，P95 69.7ms，max 75ms，墙钟 0.3s；三次复测稳定 | [本轮调优报告](reports/validation/20260904-concurrency-fix/agent-performance-report.md) |
| 历史稳定性基线 | 30 分钟，每 15 秒检查 | 119/119 cycles | [历史报告](reports/validation/20260326-150312/stability-report.md) / [JSON](reports/validation/20260326-150312/stability-report.json) |
| 真实模型检索基线（本地哈希） | `python benchmarks/real_model_eval.py --baseline-only` | Recall@5=0.900（27/30），MRR@5=0.850，avg 3ms；3 个失败用例同属"绿色防控监测频次"查询 | [检索报告](reports/validation/20260904-real-model/retrieval-report.jsonl) |
| 真实模型检索（BGE-M3+reranker） | `python benchmarks/real_model_eval.py`（SiliconFlow API） | **Recall@5=1.000（30/30）**，MRR@5=0.778，avg 739ms；原先 3 个漏检用例全部找回，但 reranker 将部分正确证据排在 rank 3-5（召回全面、排序待调优） | [检索报告](reports/validation/20260904-real-model/retrieval-report.jsonl) |
| Agent 持久化拆分（主表/步骤表/审批表） | 后端 MySQL 模式，创建 agent run + 审批 run | 三表落库：`platform_agent_runs` 结构化列齐全、`platform_agent_steps` 每步一行、`platform_agent_approvals` 审批记录 approve+决策人/时间；审批 run 从创建→waiting_approval→approve→completed 全链路 | [SQL 验证](backend/src/main/java/com/litchi/service/MysqlStateStoreService.java) |
| Agent 跨实例恢复 | 插入陈旧 running/waiting 运行 → 恢复扫描 | 陈旧 running 自动标记 `interrupted`（实测 15:47:07）；陈旧 waiting_approval 保留且跨重启 confirm(approve) 成功；列表接口 `GET /v1/agent-runs` 返回内存+MySQL 合并 | [日志](backend/logs/application.log) |
| MySQL 容灾（WSL 抖动恢复） | 后端 MySQL 模式 + 重连器 | 启动失败→15s 周期重试→自动重连成功并全量对账（1107→1507→1707 条） | [日志](backend/logs/application.log) |
| 写工具幂等 | 同一 idempotencyKey 重复 createPlan | 同键重复提交返回既有方案（planId 一致），列表中同键仅一条；不同键正常新建；无幂等键保持原行为（兼容非 Agent 调用）；Agent 审批写工具以 runId 为幂等键 | `CollaborationServiceTest.createPlanIsIdempotentByKey` / `createPlanWithoutKeyAlwaysCreatesNew`（mvn test 51/51 通过） |

## 结果边界

- 降级基线运行时 Neo4j、Milvus、Ollama 和图像模型服务均不可用，数据只能证明本地回退链路性能。
- 100 并发失败报告（19%）被有意保留在 `reports/validation/20260731-162446/`，与修复后 100% 形成对照，证明测试不是“只保留成功结果”。
- 本轮 100 并发修复在 MySQL 模式 + 三依赖全部降级（Ollama/Milvus/Neo4j 均不可用、均已预熔断）下测得，代表真实并发能力而非模型能力；真实 LLM 参与时的吞吐仍需外部环境复测。
- 历史 30 分钟稳定性报告生成于架构升级前，只能作为早期基线，不能代表当前提交的 6 小时稳定性。
- 完整 60 条评测在降级模式下运行：RAG 召回基于本地向量+BM25 混合检索，未使用真实 BGE-M3/vLLM，因此 Recall 结果只代表本地回退链路；真实模型下的 Recall@5、引用准确率与成本仍需外部环境。
- 本地哈希检索基线 Recall@5=90% 为离线脚本复算（精确复刻 SimpleEmbeddingService 算法）；真实 BGE-M3+reranker 评测已执行，Recall@5 提升至 100%、MRR 略降（0.850→0.778），该反差说明语义检索"更会找到正确证据、但排序需要进一步调优（如 reranker 阈值/融合权重）"。
- 真实模型检索评测基于 SiliconFlow 免费 API（BGE-M3 embedding + bge-reranker-v2-m3），30 条任务 22 块语料；latency avg 739ms 含 API 往返，非本地推理，不代表部署后性能。
- Agent 跨实例恢复采用"安全落地"语义：超时未推进的运行标记为 interrupted 而非自动续跑（工具执行不保证幂等，自动恢复写步骤有重复副作用风险）；等待审批的运行跨实例保留可继续决策。写工具幂等通过 `idempotencyKey`（Agent 运行 ID）在业务层查重实现：当前 MySQL 为全量投影同步架构（DELETE+INSERT），未在投影表上叠加唯一索引，幂等由 `synchronized createPlan` 查重 + 幂等键持久化共同保证；若未来改为 MySQL 主存储，可在此基础上叠加唯一索引形成双层防线。
- Policy Guard 为确定性规则拦截（不依赖模型），因此降级模式下安全评测可完整执行；真实模型下的提示注入防御仍需额外测试。

## 尚需外部环境的实验

- 真实模型下的引用准确率、端到端问答质量（当前仅完成检索 Recall/MRR 评测），以及 reranker 排序调优后的 MRR 回升验证。
- Ollama CPU 与 vLLM GPU 的 TTFT、TPOT、吞吐和成本。
- MySQL CDC 到 ClickHouse 的端到端延迟与 dbt 数据质量。
- Kubernetes Pod 删除、依赖故障、6 小时稳定性和容量拐点。
- 5-10 名目标用户任务完成率、SUS 和有效问题闭环率。

执行后应提交环境、参数、原始 JSON/CSV、报告、截图、失败样本和对应 commit SHA。
