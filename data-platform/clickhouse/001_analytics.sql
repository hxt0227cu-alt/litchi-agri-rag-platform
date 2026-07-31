CREATE DATABASE IF NOT EXISTS litchi_analytics;

CREATE TABLE IF NOT EXISTS litchi_analytics.domain_events_raw (
    event_id String,
    event_type LowCardinality(String),
    schema_version UInt16,
    tenant_id String,
    occurred_at DateTime64(3),
    trace_id String,
    payload_json String,
    ingested_at DateTime64(3) DEFAULT now64(3)
) ENGINE = MergeTree
ORDER BY (tenant_id, event_type, occurred_at, event_id);

CREATE TABLE IF NOT EXISTS litchi_analytics.agent_run_daily (
    day Date,
    tenant_id String,
    status LowCardinality(String),
    planner_mode LowCardinality(String),
    degraded UInt8,
    runs UInt64,
    avg_duration_ms Float64,
    p95_duration_ms Float64,
    input_tokens UInt64,
    output_tokens UInt64
) ENGINE = SummingMergeTree
ORDER BY (day, tenant_id, status, planner_mode, degraded);

CREATE TABLE IF NOT EXISTS litchi_analytics.consultation_daily (
    day Date,
    tenant_id String,
    disease_tag LowCardinality(String),
    status LowCardinality(String),
    consultations UInt64,
    first_response_seconds UInt64,
    completed UInt64
) ENGINE = SummingMergeTree
ORDER BY (day, tenant_id, disease_tag, status);
