import os
import sys
from datetime import datetime, timezone

import requests


def main() -> int:
    host = os.getenv("CLICKHOUSE_HOST", "clickhouse")
    query = "SELECT dateDiff('second', max(ingested_at), now()) FROM litchi_analytics.domain_events_raw"
    response = requests.post(f"http://{host}:8123/", params={"query": query}, timeout=10)
    response.raise_for_status()
    raw = response.text.strip()
    if not raw or int(raw) > int(os.getenv("CDC_FRESHNESS_SLO_SECONDS", "300")):
        print(f"CDC freshness SLO failed: {raw or 'no events'}", file=sys.stderr)
        return 1
    print(f"CDC freshness is {raw}s at {datetime.now(timezone.utc).isoformat()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
