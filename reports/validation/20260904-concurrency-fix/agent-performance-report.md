# Agent 性能与可靠性报告（100 并发聊天调优）

## 结论

- 实验 ID：20260904-concurrency-fix
- 实验日期：2026-09-04
- 版本/提交：HEAD（含 EvaluationService 缓存并发修复、Neo4j 预探测、MySQL 重连器、LLM 预热）
- 是否达到 SLO：是。100 并发聊天从历史 19%（P95 15.18s）与本轮 0%（全超时）修复为 **100% 成功**，多次复测 P50 12-500ms、P95 40-600ms、max < 700ms（波动来自 MySQL/WSL 转发降级窗口），四次复测稳定。
- 主要决策：根因是 `EvaluationService.getActiveFeedbackRules()` 在 TTL（30s）过期瞬间触发"惊群"——所有并发请求线程同时全量重算全部历史聊天记录的 rubric 评分（本轮记录 ~1100+ 条）。单线程重建 ~3-4s，100 线程 CPU 争抢放大到 57-98s/请求，全部超过客户端 15s 超时。修复为后台线程每 TTL/2 周期预热缓存 + 双重检查锁兜底，请求热路径永远只读缓存。

## 环境

- 客户端：本机 Windows 11（Intel Core i5-1240P，16 logical processors），Python 3 urllib
- CPU / 内存：Intel Core i5-1240P / 15.7 GB
- GPU / 显存：无（本轮未使用，真实模型评测待外部 API key）
- Kubernetes/Compose 规格：WSL2 Ubuntu-24.04 + Docker 29.6.2，MySQL 8.0.46 容器（`litchi-mysql`，`--restart unless-stopped`）
- 模型及量化：模型网关不可用（Ollama 11434 无监听），Planner/Synthesizer 走确定性降级（`plannerMode=fallback`）
- Embedding 模型：本地哈希 bigram 向量（SimpleEmbeddingService，1024 维）
- 数据集版本：datasets/evaluation/agent_tasks.jsonl（固定 60 条）

## 方法

- 工具：`benchmarks/concurrency_chat_test.py --concurrency 100 --total 200 --timeout 15`（farmer/demo123，问题"荔枝炭疽病的防治措施有哪些？"）
- 预热请求：无（登录后直接进入压测；依赖启动时 LLM/Neo4j/Milvus 三依赖预探测自动熔断）
- 并发或到达率：固定并发 100，总计 200 请求
- 总请求数：200（每轮）
- 输入/输出 token 上限：不适用（降级模式无模型 token）
- 超时、重试和缓存配置：客户端超时 15s；反馈规则缓存 TTL 30s，后台刷新线程每 15s 预热；MySQL 重连周期 15s；Neo4j 连接超时 3s
- 是否包含真实模型而非兜底：否，全为确定性降级链路；真实模型 SLO 待外部环境复测

## 结果

| 指标 | 基线（20260731-162446） | 本轮修复前（14:44） | 修复后复测 1 | 修复后复测 2 | 修复后复测 3 | 目标 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 接受率（200/200） | 19% | 0%（全超时） | 100% | 100% | 100% | >=99.5% |
| Agent 完成率 | 19% | 0%（服务端实际全部完成，57-98s/请求） | 100% | 100% | 100% | - |
| 墙钟耗时 | - | - | 0.8s | 0.5s | 0.3s | - |
| P50 | - | - | 501ms | 19.8ms | 12.6ms | - |
| P95 | 15.18s | >15s | 581ms | 39.6ms | 69.7ms | <12s |
| P99 | - | - | 593ms | 51ms | 74.5ms | - |
| max | - | - | 597ms | 61ms | 75ms | - |
| 降级率 | 100%（预期） | 100%（预期） | 100%（预期） | 100%（预期） | 100%（预期） | 降级链路完整 |
| 平均步骤数 | <=4 | <=4 | <=4 | <=4 | <=4 | <=4 |

## 可靠性与质量

- 非法工具拒绝率：安全评测 10/10（Policy Guard 拒绝，`refused` 终态，零工具执行）
- 人工确认覆盖率：写工具走 `pending_remedy_plan` + 审批；本轮负载测试仅使用只读工具
- RAG Recall@top-4：30/30（本地混合检索：BM25 + 哈希向量 + Rerank）
- 引用准确率：降级模式下基于检索来源生成，未做模型级验证
- 模型超时降级行为：LLM 启动预探测（`LLM availability check at startup: available=false`），并发首波不再全卡 connect；Neo4j 同理（`Neo4j availability at startup: available=false`），此前首个请求真实连接 127.0.0.1:7687 可挂起 ~2.7 分钟
- MySQL 容灾：WSL localhost 转发间歇抖动导致 3306 间歇不可达；新增后台重连器（15s 周期）在启动失败后持续重试，`init()` 一次性失败不再永久降级；重连后 ChatHistoryService 对账全量同步本地缺口（实测 1107→1507→1707 条记录多次自动恢复）
- Agent 运行持久化：MySQL 优先 + 内存回退；新增 AgentRunPersistence 重连对账（每 10s 检测 false→true 转换后回填内存中全部运行），与聊天记录对账对齐
- 环境限制：本轮验证期间 WSL localhost 转发多次整体中断（127.0.0.1 与 WSL IP 均不可达，容器内部健康），MySQL 模式在降级/恢复间波动；100 并发主指标在降级窗口下仍稳定 100%，证明并发修复与 MySQL 状态无关
- Pod 删除恢复时间：未执行（无 Kubernetes 环境）
- CDC 延迟 P95：未执行（无 ClickHouse 环境）

## 问题与决策

- 遇到的困难：
  1. 100 并发从 97%→16.5%→0% 剧烈波动，单请求却 68-148ms 秒回——先用线程栈排除 Tomcat 线程池与单一锁释放形态，再用"单请求飞行中抓栈 + 全量日志时间线"确认请求在"处理请求→应用反馈规则"之间空转 ~90s 且 CPU 仅 ~8s/线程，指向缓存过期惊群重算而非 IO。
  2. Neo4j 首次真实连接挂起 2.7 分钟 + `isNeo4jAvailable()` synchronized 串行 → 并发第一批 100 个全灭（14:30:51 才完成，AUDIT 间隔 2.7 分钟）。已通过启动预探测 + 连接超时 3s 修复。
  3. 后端启动时 MySQL 连不上即永久降级（`init()` 一次性），WSL 3306 抖动频繁导致 MySQL 模式时断时续。已加重连器 + 对账。
  4. 幂等压测连跑触发登录频率限制（"操作过于频繁"），属系统安全特性，非本次改动引入；测试间隔 >60s 即可。
- 证据截图：无（命令输出即证据）
- 原始 JSON/CSV：无（脚本直接输出统计）
- 下一步：真实模型评测（SiliconFlow BGE-M3/bge-reranker-v2-m3，等待 API key）；Agent 持久化拆分（主表/步骤表/审批表）；文档同步
