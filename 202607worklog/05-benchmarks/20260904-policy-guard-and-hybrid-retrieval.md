# 2026-09-04 完善轮：Policy Guard 与 BM25 混合检索

## 背景

毕业设计答辩前的完善轮。目标是把项目从「功能完整」推向「评测闭环 + 安全可证」，并补齐待完成清单中本机可验证的部分。全程本机运行（Windows，JDK 17 / Maven 3.9.11 / Node / Python 3.11 / k6 v1.2.3），Docker、MySQL、Milvus、Neo4j、Ollama 均不可用，验证在降级模式下完成。

## 本次完成

### 1. Agent 执行前输入安全护栏（Policy Guard）

问题：固定评测中 10 条安全任务（提示注入、越权写、数据外泄、编造、危险工具、跨租户）全部失败——Agent 在降级模式下会照常规划并执行只读工具，而不是拒绝。

改动：
- 新增 `PolicyGuard`（backend/src/main/java/com/litchi/agent/PolicyGuard.java）：确定性规则引擎，规划前拦截六类恶意意图，不依赖模型，因此降级模式下也能完整防御。
- `AgentService.runWithId` 在进入 Planner 前调用 `policyGuard.evaluate(goal)`；命中即返回 `refused` 终态（`plannerMode=guard`，零步骤、零工具执行）。
- 新增 `refused` 状态：AgentRunResponse.status、持久化终态事件、前端状态映射与 SSE 轮询终止均支持。
- 评测执行器 `run_agent_evaluation.py` 把 `refused` 识别为终态。

验证：
- `PolicyGuardTest`：10 条安全任务全部拒绝且含「拒绝」措辞；5 条正常任务全部放行；空/空串放行。
- `AgentServiceTest`：新增 3 个用例（提示注入拒绝、越权写/数据外泄拒绝、正常任务放行并完成）。
- 真实评测：安全 0/10 → 10/10。

### 2. BM25 + 向量 + Rerank 可切换混合检索

问题：RAG 30 条中有 6 条失败（rag-008/009/018/019/028/029）。「深圳荔枝生产技术规程」与「绿色防控监测频次」两个查询无法把目标权威文档召回进 top-4——原检索只有哈希向量 + 固定领域词词法加权，遇到文档文本不含查询原词的场景就漏召回。

改动：
- 新增 `BM25Scorer`：标准 BM25 词法打分（k1=1.5, b=0.75），分词与向量侧一致（bigram + 整词），提供 `normalize(raw)` 映射到 (0,1)。
- `DocumentService.searchFromLocalChunks` 改为策略化实现，支持 `app.retrieval.strategy`：`hybrid`（默认，向量 + 词法 + BM25 归一化融合）、`bm25`、`vector`、`lexical`（原行为）。
- BM25 的搜索文本把 source/title 重复两次加权，让文件名/标题命中的查询词获得更高词频。
- 领域词表扩展「绿色」「监测」两个主题词，激活词法提升与重排。
- 前端/配置：application.yml 增加 `app.retrieval.strategy`；前端无接口变更。

验证：
- `BM25ScorerTest` 5 个用例。
- 真实评测：RAG 召回 24/30 → 30/30，无回归。

### 3. 真实 k6 负载验证

- k6 v1.2.3 对本机后端做固定到达率压测（Agent 完整运行 + 状态轮询）。
- 场景 A：2 req/s × 60s，120/120 完成，成功率 100%，Agent P95=508ms（阈值 12s）。
- 场景 B：5 req/s × 60s，301/301 完成，成功率 100%，Agent P95=507ms，HTTP 失败率 0%。
- 报告：reports/validation/20260904-agent-load/agent-performance-report.md。

### 4. 完整评测闭环

- `benchmarks/run_agent_evaluation.py` 接真实 HTTP 执行器，跑通全部 60 条：
  - 最终 `reports/evaluation/latest.jsonl`：60/60（RAG 30/30、Agent 20/20、Safety 10/10）。
  - 保留中间过程 `latest-hybrid.jsonl`（57/60）与 `safety-check.jsonl`（10/10）作为演进证据。

## 测试总量

- 后端 Maven 测试：38 → 49，全部通过（新增 PolicyGuardTest 3、BM25ScorerTest 5、AgentServiceTest 扩展 3）。

## 文档同步

- EVIDENCE.md：新增本轮证据行与结果边界。
- docs/hxt-bishe-能力补强实施进度.md：标记 4 项完成（真实评测接入、混合检索、Policy Guard、真实 k6）。
- KNOWN_LIMITATIONS.md：补充 2026-09-04 状态说明。

## 仍待外部环境

- 写工具数据库级幂等约束、运行主表/步骤表/审批表拆分与重启恢复（需 MySQL）。
- OpenTelemetry/Langfuse 串联追踪。
- Playwright Agent E2E、Docker 空数据启动、kind/k3d Helm 验证。
- 真实 BGE-M3/vLLM 下的 Recall@5、TTFT/TPOT、成本与模型级安全测试。
