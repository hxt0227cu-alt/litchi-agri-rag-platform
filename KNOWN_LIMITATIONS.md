# Known Limitations

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
