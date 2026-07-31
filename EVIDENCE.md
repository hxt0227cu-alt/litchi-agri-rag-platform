# Evidence Index

本文件把仓库能力、测试方法和量化结果绑定到可复现证据。所有“通过”均来自真实命令；目标值与未执行实验不会写成实测结果。

## 测试环境

| 项目 | 配置 |
| --- | --- |
| 日期 | 2026-07-31 |
| OS | Windows 11 10.0.26200 |
| CPU | Intel Core i5-1240P，16 logical processors |
| 内存 | 15.7 GB |
| Java / Maven | OpenJDK 17.0.19 / Maven 3.9.11 |
| Node / Python | Node 22.23.1 / Python 3.11.9 |
| Docker / GPU | 本轮不可用，相关实验未执行 |

## 证据矩阵

| 能力声明 | 命令或方法 | 实际结果 | 证据 |
| --- | --- | --- | --- |
| 后端测试 | `cd backend; mvn -B test` | 38 tests，0 failures/errors/skips | [本地验证报告](reports/verification/20260731-local-verification.md) |
| 前端权限 | `cd frontend; npm test` | passed | [本地验证报告](reports/verification/20260731-local-verification.md) |
| 前端构建 | `npm run build` | 1,729 modules，24.96s | [本地验证报告](reports/verification/20260731-local-verification.md) |
| Agent 任务集 | `python benchmarks/evaluate_agent.py --validate-only` | 60 条，`valid=true` | [任务集](datasets/evaluation/agent_tasks.jsonl) |
| 降级基线 | `measure-api-baseline.ps1` | Chat avg 159.88ms；Diagnosis avg 47.43ms | [报告](reports/validation/20260731-160911/baseline-report.md) / [JSON](reports/validation/20260731-160911/baseline-report.json) |
| 100 并发聊天 | 100 concurrency，200 requests，15s timeout | 未通过：19% success，P95 15.18s | [失败报告](reports/validation/20260731-162446/concurrency-report.md) / [JSON](reports/validation/20260731-162446/concurrency-report.json) |
| 历史稳定性基线 | 30 分钟，每 15 秒检查 | 119/119 cycles | [历史报告](reports/validation/20260326-150312/stability-report.md) / [JSON](reports/validation/20260326-150312/stability-report.json) |

## 结果边界

- 降级基线运行时 Neo4j、Milvus、Ollama 和图像模型服务均不可用，数据只能证明本地回退链路性能。
- 100 并发失败报告被有意保留，用于证明测试不是“只保留成功结果”。
- 历史 30 分钟稳定性报告生成于架构升级前，只能作为早期基线，不能代表当前提交的 6 小时稳定性。
- 60 条任务集已完成结构校验，尚无真实 BGE-M3/vLLM 结果，因此不能声称 Recall@5、引用准确率或模型成本达到目标。

## 尚需外部环境的实验

- BGE-M3 与哈希向量对比，以及 Recall@5、引用准确率。
- Ollama CPU 与 vLLM GPU 的 TTFT、TPOT、吞吐和成本。
- MySQL CDC 到 ClickHouse 的端到端延迟与 dbt 数据质量。
- Kubernetes Pod 删除、依赖故障、6 小时稳定性和容量拐点。
- 5-10 名目标用户任务完成率、SUS 和有效问题闭环率。

执行后应提交环境、参数、原始 JSON/CSV、报告、截图、失败样本和对应 commit SHA。
