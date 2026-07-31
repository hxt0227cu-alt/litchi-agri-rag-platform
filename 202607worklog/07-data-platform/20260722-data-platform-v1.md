# 2026-07-22 数据平台 V1

## 已完成

- 增加租户、果园和 Outbox 业务表。
- Agent 终态写入版本化领域事件，避免业务库与消息发布双写不一致。
- 增加 Kafka、Debezium、ClickHouse、dbt、Airflow 质量检查和 CDC 契约骨架。
- 增加 Agent 日汇总与 CDC 新鲜度检查模型。

## 设计取舍

第一版不引入 Flink/Iceberg/OpenMetadata 全套组件。当前业务量和硬件不足以证明它们的必要性；Kafka + ClickHouse + dbt 已能展示 CDC、实时聚合、分层建模、质量测试和血缘入口。只有当分钟级实时计算或多源湖仓需求被验证后再扩展。
