# 轻量数据中台

数据平台消费 MySQL CDC 事件，为业务看板、Agent 评测、服务运营和性能报告提供统一数据口径。

## 分层

- ODS：Kafka 原始事件和 MinIO Parquet 快照。
- DWD：ClickHouse 中按事件规范清洗后的明细。
- DWS：按租户、区域、病症、角色和日期聚合的主题指标。
- ADS：面向业务页面、Superset 和性能报告的宽表。

本地只建议在已有业务 Compose 之外按需启动 `data-platform/docker-compose.yml`。Kafka、Debezium 和 ClickHouse 使用独立资源，不纳入 16GB 本机的默认启动链路。Airflow 与 dbt 在云端或 CI 中执行。

## CDC 注册

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8083/connectors -ContentType 'application/json' -InFile data-platform/kafka/mysql-source.json
```

连接器配置不保存密码，使用环境变量或 Secret 注入。生产环境还需启用 Kafka TLS/SASL 和 ClickHouse 用户权限。
