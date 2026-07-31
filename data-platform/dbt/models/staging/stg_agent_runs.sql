{{ config(materialized='view') }}

select
    toDate(occurred_at) as day,
    tenant_id,
    JSONExtractString(payload_json, 'status') as status,
    JSONExtractString(payload_json, 'usage', 'plannerMode') as planner_mode,
    toUInt8(JSONExtractBool(payload_json, 'degraded')) as degraded,
    toUInt64OrZero(JSONExtractUInt(payload_json, 'durationMs')) as duration_ms,
    payload_json
from litchi_analytics.domain_events_raw
where event_type = 'agent.run.completed'
