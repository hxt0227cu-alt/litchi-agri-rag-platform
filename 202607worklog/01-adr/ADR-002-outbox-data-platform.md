# ADR-002：使用 Outbox + Debezium 构建数据平台入口

- 日期：2026-07-22
- 状态：已采纳

## 决策

Agent 终态与业务事务先写入 MySQL Outbox，由 Debezium 捕获 Binlog 并进入 Kafka。ClickHouse、dbt 和 Airflow 消费或加工标准领域事件。

## 原因

应用直接同时写 MySQL 和 Kafka 会产生双写不一致。Outbox 允许业务状态与待发布事件共享数据库一致性边界，并能通过事件 ID 实现幂等消费。

## 数据边界

事件只保存运行状态、风险、耗时、工具名称和步骤摘要，不保存完整问题、提示词或知识片段，避免分析平台复制敏感业务内容。
