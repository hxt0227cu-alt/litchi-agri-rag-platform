{{ config(materialized='table') }}

select
    day,
    tenant_id,
    status,
    planner_mode,
    degraded,
    count() as runs,
    avg(duration_ms) as avg_duration_ms,
    quantile(0.95)(duration_ms) as p95_duration_ms,
    0 as input_tokens,
    0 as output_tokens
from {{ ref('stg_agent_runs') }}
group by day, tenant_id, status, planner_mode, degraded
