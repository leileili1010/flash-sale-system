"""Generates dashboards/flash-sale.json. Run after editing the panel list: python build_dashboard.py"""
import json
from pathlib import Path

PURCHASE_URI = 'uri="/api/drops/{dropId}/purchase"'

panels = [
    ("Purchase attempts per second, by outcome", "timeseries", "reqps",
     [("sum by (outcome) (rate(flashsale_purchase_attempts_total[$__rate_interval]))", "{{outcome}}")]),
    ("Purchase latency", "timeseries", "s",
     [(f"histogram_quantile({q}, sum by (le) (rate(http_server_requests_seconds_bucket{{{PURCHASE_URI}}}[$__rate_interval])))", label)
      for q, label in ((0.5, "p50"), (0.95, "p95"), (0.99, "p99"))]),
    ("Orders processed per second, by outcome", "timeseries", "reqps",
     [("sum by (outcome) (rate(flashsale_orders_processed_total[$__rate_interval]))", "{{outcome}}")]),
    ("Order consumer lag (records)", "timeseries", "short",
     [("max(kafka_consumer_fetch_manager_records_lag_max{client_id=~\".*order-creation.*\"})", "max lag")]),
    ("HTTP requests per second, by status", "timeseries", "reqps",
     [("sum by (status) (rate(http_server_requests_seconds_count[$__rate_interval]))", "{{status}}")]),
    ("Database connections", "timeseries", "short",
     [("sum(hikaricp_connections_active)", "active"), ("sum(hikaricp_connections_pending)", "waiting"),
      ("sum(hikaricp_connections_max)", "max")]),
    ("JVM heap used", "timeseries", "bytes",
     [("sum(jvm_memory_used_bytes{area=\"heap\"})", "heap used")]),
    ("Live threads", "timeseries", "short",
     [("jvm_threads_live_threads", "platform threads")]),
]


def panel(index, title, kind, unit, targets):
    return {
        "id": index + 1,
        "title": title,
        "type": kind,
        "datasource": {"type": "prometheus", "uid": "prometheus"},
        "gridPos": {"h": 8, "w": 12, "x": 12 * (index % 2), "y": 8 * (index // 2)},
        "fieldConfig": {"defaults": {"unit": unit, "custom": {"lineWidth": 2, "fillOpacity": 12}}, "overrides": []},
        "options": {"legend": {"displayMode": "list", "placement": "bottom"}, "tooltip": {"mode": "multi"}},
        "targets": [
            {"refId": chr(65 + i), "expr": expr, "legendFormat": legend,
             "datasource": {"type": "prometheus", "uid": "prometheus"}}
            for i, (expr, legend) in enumerate(targets)
        ],
    }


dashboard = {
    "uid": "flash-sale",
    "title": "Flash Sale",
    "tags": ["flash-sale"],
    "timezone": "browser",
    "refresh": "5s",
    "time": {"from": "now-15m", "to": "now"},
    "schemaVersion": 39,
    "panels": [panel(i, *p) for i, p in enumerate(panels)],
}

out = Path(__file__).parent / "dashboards" / "flash-sale.json"
out.write_text(json.dumps(dashboard, indent=2) + "\n", encoding="utf-8")
print(f"wrote {out}")
