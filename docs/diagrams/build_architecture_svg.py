"""Generates docs/images/architecture.svg, the illustrated system overview.

Usage (from the repository root):
    python docs/diagrams/build_architecture_svg.py docs/images/architecture.svg frontend/public/favicon.svg
"""
import sys
from pathlib import Path

OUT = Path(sys.argv[1])
FONT = "'Segoe UI', -apple-system, 'Helvetica Neue', Arial, sans-serif"

THEMES = {
    "blue":   dict(fill="#EEF4FF", stroke="#A9C1F5", title="#1E3A8A", sub="#5B6B8C", icon="#2563EB"),
    "purple": dict(fill="#F4EFFF", stroke="#C3B0F2", title="#4C1D95", sub="#6B5F8A", icon="#7C3AED"),
    "green":  dict(fill="#EAF8F1", stroke="#98D7B6", title="#065F46", sub="#4F7466", icon="#059669"),
    "teal":   dict(fill="#E8F7F8", stroke="#8BD3DA", title="#0E5A63", sub="#4D7478", icon="#0891B2"),
    "orange": dict(fill="#FFF4E8", stroke="#F6BE84", title="#9A3412", sub="#8A6A55", icon="#EA6A1A"),
    "red":    dict(fill="#FFEFF1", stroke="#F3A7B0", title="#9F1239", sub="#8C5E66", icon="#E11D48"),
    "pg":     dict(fill="#EDF3FC", stroke="#9DBBE3", title="#1E3A6E", sub="#566A87", icon="#336791"),
    "amber":  dict(fill="#FFF8E6", stroke="#F3CF7A", title="#8A5A00", sub="#86704A", icon="#F59E0B"),
}

COLORS = dict(request="#2563EB", redis="#E11D48", db="#F08A24", event="#7C3AED", metrics="#F59E0B")


def lucide(paths, color, x, y, size=34, width=2.0, fill="none"):
    s = size / 24
    return (f'<g transform="translate({x} {y}) scale({s})" fill="{fill}" stroke="{color}" '
            f'stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round">{paths}</g>')


ICONS = {
    "shield": lambda c, x, y: lucide(
        '<path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 '
        '6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" fill="#DDD3FB"/><path d="m9 12 2 2 4-4"/>', c, x, y),
    "gauge": lambda c, x, y: lucide(
        '<path d="M3.34 19a10 10 0 1 1 17.32 0z" fill="#E7DEFC"/><path d="m12 14 4-4"/><circle cx="12" cy="14" r="1.3" fill="#7C3AED"/>', c, x, y),
    "zap": lambda c, x, y: lucide(
        '<path d="M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 '
        '.78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z" fill="#FFD0D8"/>', c, x, y),
    "send": lambda c, x, y: lucide(
        '<path d="M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 '
        '3.18a2 2 0 0 1 1.112 1.11z" fill="#C9F0DC"/><path d="m21.854 2.147-10.94 10.939"/>', c, x, y),
    "card": lambda c, x, y: lucide(
        '<rect width="20" height="14" x="2" y="5" rx="2" fill="#C9F0DC"/><line x1="2" x2="22" y1="10" y2="10" stroke-width="2.6"/>'
        '<line x1="6" x2="10" y1="15" y2="15"/>', c, x, y),
    "clock": lambda c, x, y: lucide(
        '<circle cx="12" cy="12" r="10" fill="#CDEFF3"/><polyline points="12 6 12 12 16 14"/>', c, x, y),
    "sync": lambda c, x, y: lucide(
        '<path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/>'
        '<path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M8 16H3v5"/>', c, x, y, width=2.4),
    "gear": lambda c, x, y: lucide(
        '<path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38'
        'a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73'
        'l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25'
        'a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74'
        'l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z" '
        'fill="#C9F0DC"/><circle cx="12" cy="12" r="3" fill="#fff"/>', c, x, y),
    "monitor": lambda c, x, y: lucide(
        '<path d="M18 8V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h8" fill="#D8E5FF"/><path d="M10 19v-3.96 3.15"/>'
        '<path d="M7 19h5"/><rect width="6" height="10" x="16" y="12" rx="2" fill="#D8E5FF"/>', c, x, y, size=30),
    "chart": lambda c, x, y: lucide(
        '<path d="M3 3v16a2 2 0 0 0 2 2h16"/><path d="M18 17V9" stroke-width="3"/><path d="M13 17V5" stroke-width="3"/>'
        '<path d="M8 17v-3" stroke-width="3"/>', c, x, y, size=28),
    "leaf": lambda c, x, y: lucide(
        '<path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z" fill="#B8E3A0"/>'
        '<path d="M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12"/>', c, x, y, size=28),
    "database": lambda c, x, y: lucide(
        '<ellipse cx="12" cy="5" rx="9" ry="3" fill="#D6E4F7"/><path d="M3 5V19A9 3 0 0 0 21 19V5"/>'
        '<path d="M3 12A9 3 0 0 0 21 12"/>', c, x, y, size=28),
    "flame": lambda c, x, y: (
        f'<g transform="translate({x} {y})"><circle cx="17" cy="17" r="17" fill="#E6522C"/>'
        '<path d="M17 6c1 4 6 6 6 12a6 6 0 0 1-12 0c0-3 2-4.5 2-7 2 1.5 2.5 3 2.5 4.5C17 13 17 9 17 6z" fill="#fff"/>'
        '<rect x="10.5" y="25" width="13" height="2.4" rx="1.2" fill="#fff"/></g>'),
}


def react_icon(x, y):
    return (f'<g transform="translate({x + 18} {y + 18})" fill="none" stroke="#0EA5E9" stroke-width="2">'
            '<ellipse rx="16" ry="6.2"/><ellipse rx="16" ry="6.2" transform="rotate(60)"/>'
            '<ellipse rx="16" ry="6.2" transform="rotate(120)"/><circle r="3" fill="#0EA5E9" stroke="none"/></g>')


def kafka_icon(x, y):
    c = "#EA6A1A"
    return (f'<g transform="translate({x} {y})" stroke="{c}" stroke-width="2.4" fill="#FFE2C7">'
            '<line x1="17" y1="8" x2="17" y2="28"/><line x1="17" y1="18" x2="27" y2="12"/><line x1="17" y1="18" x2="27" y2="25"/>'
            '<circle cx="17" cy="5" r="4"/><circle cx="17" cy="18" r="5"/><circle cx="17" cy="31" r="4"/>'
            '<circle cx="28.5" cy="11" r="3.6"/><circle cx="28.5" cy="26" r="3.6"/></g>')


def redis_icon(x, y):
    layer = lambda dy, top: (
        f'<path d="M3 {14 + dy}l15 -6 15 6 -15 6z" fill="{top}"/>'
        f'<path d="M3 {14 + dy}v4l15 6 15-6v-4l-15 6z" fill="#B91C1C"/>')
    return (f'<g transform="translate({x} {y - 2})">{layer(16, "#F87171")}{layer(8, "#EF4444")}{layer(0, "#DC2626")}'
            '<path d="M14 11.5l4-1.6 4 1.6-4 1.6z" fill="#fff"/></g>')


def pg_icon(x, y):
    return (f'<g transform="translate({x} {y})"><path d="M4 7v22c0 2.8 6.3 5 14 5s14-2.2 14-5V7z" fill="#336791"/>'
            '<ellipse cx="18" cy="7" rx="14" ry="5" fill="#5B8BC4"/>'
            '<path d="M4 15c0 2.8 6.3 5 14 5s14-2.2 14-5M4 22c0 2.8 6.3 5 14 5s14-2.2 14-5" fill="none" stroke="#9DBBE3" stroke-width="1.6"/></g>')


def grafana_icon(x, y):
    return (f'<g transform="translate({x + 17} {y + 17})" fill="none" stroke-linecap="round">'
            '<path d="M0 -14A14 14 0 1 1 -12.1 7" stroke="#F46800" stroke-width="4.5"/>'
            '<path d="M-7 -4A8 8 0 1 1 0 8" stroke="#FBBF24" stroke-width="4"/>'
            '<circle r="2.6" fill="#F46800" stroke="none"/></g>')


ICONS.update(react=lambda c, x, y: react_icon(x, y), kafka=lambda c, x, y: kafka_icon(x, y),
             redis=lambda c, x, y: redis_icon(x, y), pg=lambda c, x, y: pg_icon(x, y),
             grafana=lambda c, x, y: grafana_icon(x, y))

parts = []
add = parts.append


def text(x, y, s, size=13, weight=400, color="#334155", anchor="start", spacing=None, italic=False):
    ls = f' letter-spacing="{spacing}"' if spacing else ""
    st = ' font-style="italic"' if italic else ""
    add(f'<text x="{x}" y="{y}" font-size="{size}" font-weight="{weight}" fill="{color}" text-anchor="{anchor}"{ls}{st}>{s}</text>')


def card(x, y, w, h, theme, icon, title, lines, title_size=15):
    t = THEMES[theme]
    add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="{t["fill"]}" stroke="{t["stroke"]}" '
        f'stroke-width="1.6" filter="url(#shadow)"/>')
    add(ICONS[icon](t["icon"], x + 13, y + h / 2 - 17))
    tx = x + 60
    block = 18 + 15 * len(lines)
    ty = y + (h - block) / 2 + 14
    text(tx, ty, title, title_size, 700, t["title"])
    for i, line in enumerate(lines):
        text(tx, ty + 18 + 15 * i, line, 11.5, 400, t["sub"])


def group(x, y, w, h, fill, stroke, dash=None, rx=20):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="1.6"{d}/>')


def line(d, kind, dash=None, start=False, end=True, width=2):
    color = COLORS[kind]
    da = f' stroke-dasharray="{dash}"' if dash else ""
    ms = f' marker-start="url(#s-{kind})"' if start else ""
    me = f' marker-end="url(#e-{kind})"' if end else ""
    add(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{width}"{da}{ms}{me}/>')


def label(x, y, s, color, anchor="middle", size=11.5, weight=600):
    text(x, y, s, size, weight, color, anchor)


def pill(cx, top, lines, color, size=11.5, char=6.4):
    w = max(len(l) for l in lines) * char + 14
    h = 15 * len(lines) + 8
    add(f'<rect x="{cx - w / 2}" y="{top}" width="{w}" height="{h}" rx="7" fill="#FFFFFF" stroke="{color}" stroke-opacity="0.35"/>')
    for i, l in enumerate(lines):
        text(cx, top + 16 + 15 * i, l, size, 600, color, "middle")


W, H = 1600, 960
# columns of the backend cards
CW, GAP = 182, 20
COL = [345 + i * (CW + GAP) for i in range(4)]
MID = [c + CW / 2 for c in COL]
R1Y, R2Y, CH = 285, 610, 80
KY = 420

# ---------- title ----------
logo = Path(sys.argv[2]).read_text(encoding="utf-8")
logo_inner = logo[logo.index(">", logo.index("<svg")) + 1: logo.rindex("</svg>")]
add('<circle cx="74" cy="66" r="44" fill="#FDEAF2"/>')
add(f'<svg x="40" y="30" width="68" height="68" viewBox="0 0 64 64">{logo_inner}</svg>')
add('<path d="M117 30l3 7 7 3-7 3-3 7-3-7-7-3 7-3z" fill="#F7B733"/>')
text(140, 76, "Flash Sale System — Architecture", 36, 800, "#1E2A5A")
text(142, 108, "Redis-gated purchases   •   Event-driven orders   •   Database-enforced invariants   •   Never oversells",
     16, 500, "#7C88A6")

# ---------- containers ----------
group(30, 215, 255, 215, "#F3F7FF", "#C9D8FA")                      # client
add(ICONS["monitor"]("#2563EB", 48, 232))
text(90, 252, "Client", 17, 700, "#1E3A8A")
text(90, 270, "Web and mobile browsers", 12, 400, "#5B6B8C")

group(310, 145, 860, 655, "#F8F9FF", "#D3DAF3")                     # backend
add(ICONS["leaf"]("#3F9142", 330, 160))
text(366, 182, "Backend", 18, 700, "#1E2A5A")
text(445, 182, "Spring Boot 4 · Java 25 virtual threads · stateless, scales to N instances", 13, 400, "#64748B")

group(330, 197, 820, 188, "#FFFFFF", "#C7D2FE", dash="6 5", rx=16)    # purchase path
text(348, 220, "PURCHASE PATH  ·  touches only Redis and Kafka", 11, 700, "#6D5BD0", spacing="0.6")
group(330, 545, 820, 190, "#FFFFFF", "#A7E3C6", dash="6 5", rx=16)    # async work
text(348, 568, "ASYNCHRONOUS AND SCHEDULED WORK", 11, 700, "#2F8F6B", spacing="0.6")

group(1195, 145, 375, 655, "#F7F9FD", "#D3DAF3")                    # data stores
add(ICONS["database"]("#336791", 1215, 160))
text(1251, 182, "Data stores", 18, 700, "#1E2A5A")

group(30, 545, 255, 255, "#FFF8F3", "#F6CDB0")                      # observability
add(ICONS["chart"]("#E6522C", 48, 562))
text(88, 584, "Observability", 17, 700, "#8A2E13")

# ---------- connectors (drawn under the cards) ----------
# client -> API
line(f"M265 350 C 295 350, 310 325, {COL[0] - 2} 325", "request")
pill(298, 284, ["HTTPS", "+ Bearer JWT"], COLORS["request"], char=7.4)
# purchase path chain
for i in range(3):
    line(f"M{COL[i] + CW} 325 L{COL[i + 1] - 2} 325", "request")
# rate limiter and reservation <-> Redis
line(f"M{MID[1]} {R1Y - 2} V250 Q{MID[1]} 240 {MID[1] + 10} 240 H1223", "redis", start=True)
line(f"M{MID[2]} {R1Y - 2} V268 Q{MID[2]} 258 {MID[2] + 10} 258 H1223", "redis", start=True, width=2.6)
label(1095, 234, "per-user rate bucket", COLORS["redis"])
label(1095, 275, "EVAL reserve.lua (atomic)", COLORS["redis"])
# API -> payment
line(f"M{MID[0]} {R1Y + CH} V{R2Y - 2}", "request")
label(MID[0] + 10, 470, "POST /orders/{no}/pay", COLORS["request"], anchor="start")
# sweeper / reconciliation -> Redis (under the Kafka arrow)
line(f"M{MID[1]} {R2Y} V532 Q{MID[1]} 522 {MID[1] + 10} 522 H1290 Q1300 522 1300 512 V357", "redis", dash="7 5")
line(f"M{MID[2]} {R2Y} V522", "redis", dash="7 5", end=False)
label(745, 514, "release unit  ·  compare stock", COLORS["redis"])
# publisher -> Kafka -> consumer
line(f"M{MID[3]} {R1Y + CH} V{KY - 2}", "event", dash="6 5", width=2.4)
label(MID[3] - 10, 400, "OrderRequestedEvent", COLORS["event"], anchor="end")
add(f'<rect x="{MID[3] - 6}" y="514" width="12" height="16" fill="#F8F9FF"/>')
line(f"M{MID[3]} {KY + 80} V{R2Y - 2}", "event", dash="6 5", width=2.4)
label(MID[3] - 10, 596, "consume, key = drop id", COLORS["event"], anchor="end")
# consumer -> PostgreSQL
line(f"M{COL[3] + CW} 650 H1223", "db", width=2.6)
pill(1179, 604, ["insert order", "+ lock unit"], COLORS["db"], size=11, char=6.1)
# payment / sweeper / reconciliation -> PostgreSQL (bus)
for x in MID[:3]:
    line(f"M{x} {R2Y + CH} V760", "db", end=False)
    add(f'<circle cx="{x}" cy="760" r="3.5" fill="{COLORS["db"]}"/>')
line(f"M{MID[0]} 760 H1375 Q1385 760 1385 750 V707", "db")
label(745, 782, "compare-and-set status updates  ·  close unpaid orders, release stock  ·  read for drift", COLORS["db"])
# observability
line("M265 632 H330", "metrics", dash="2 5", width=2.4)
line("M157 667 V703", "db")

# ---------- cards ----------
card(50, 298, 215, 104, "blue", "react", "React 19 SPA", ["Vite · TypeScript", "Tailwind · TanStack Query", "served by nginx"])

card(COL[0], R1Y, CW, CH, "purple", "shield", "REST API", ["JWT resource server", "RFC 9457 errors"])
card(COL[1], R1Y, CW, CH, "purple", "gauge", "Rate limiter", ["Bucket4j", "global + per user"])
card(COL[2], R1Y, CW, CH, "red", "zap", "Reservation", ["window · one per user", "stock, in one step"])
card(COL[3], R1Y, CW, CH, "green", "send", "Order publisher", ["Snowflake order no.", "idempotent producer"], 14.5)

card(COL[3], KY, CW, CH, "orange", "kafka", "Apache Kafka", ["orders.requested", "6 partitions + DLT"])

card(COL[0], R2Y, CW, CH, "green", "card", "Payment", ["simulated checkout", "compare-and-set"])
card(COL[1], R2Y, CW, CH, "teal", "clock", "Expiry sweeper", ["every 5 s · ShedLock", "SKIP LOCKED"], 14.5)
card(COL[2], R2Y, CW, CH, "orange", "sync", "Reconciliation", ["Redis vs database", "drift report"], 14.5)
card(COL[3], R2Y, CW, CH, "green", "gear", "Order consumer", ["idempotent insert", "retries, then DLT"], 14.5)

card(1225, 215, 320, 142, "red", "redis", "Redis 8", ["stock counters · buyer sets", "drop windows · catalog cache",
                                                     "rate-limit buckets", "Lua scripts run atomically"], 17)
card(1225, 575, 320, 132, "pg", "pg", "PostgreSQL 18", ["system of record · Flyway", "CHECK: available + locked + sold = total",
                                                         "one live order per customer"], 17)

card(50, 597, 215, 70, "orange", "flame", "Prometheus", ["scrapes /actuator/", "prometheus every 5 s"])
card(50, 705, 215, 70, "amber", "grafana", "Grafana", ["Flash Sale dashboard"])

# ---------- legend ----------
group(30, 830, 1540, 88, "#FFFFFF", "#DCE2F2", rx=16)
text(56, 880, "Legend", 15, 700, "#1E2A5A")
items = [("request", None, "HTTP request"), ("redis", None, "Redis script / bucket"), ("db", None, "Database write"),
         ("event", "6 5", "Kafka event"), ("metrics", "2 5", "Metrics scrape")]
lx = 170
for kind, dash, name in items:
    line(f"M{lx} 875 h52", kind, dash=dash, width=2.4)
    text(lx + 64, 880, name, 13.5, 500, "#475569")
    lx += 255

# ---------- document ----------
markers = "".join(
    f'<marker id="e-{k}" viewBox="0 0 10 10" refX="8.5" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">'
    f'<path d="M0 0L10 5L0 10z" fill="{c}"/></marker>'
    f'<marker id="s-{k}" viewBox="0 0 10 10" refX="8.5" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">'
    f'<path d="M0 0L10 5L0 10z" fill="{c}"/></marker>'
    for k, c in COLORS.items())
svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" font-family="{FONT}" role="img" aria-labelledby="title desc">
<title id="title">Flash Sale System architecture</title>
<desc id="desc">A React single-page app calls a stateless Spring Boot backend. Purchases pass a rate limiter and an atomic Redis Lua reservation, then an order event goes to Kafka; a consumer writes the order to PostgreSQL. Payment, an expiry sweeper and reconciliation update PostgreSQL and Redis. Prometheus scrapes metrics for Grafana.</desc>
<defs>{markers}
<filter id="shadow" x="-10%" y="-10%" width="120%" height="140%"><feDropShadow dx="0" dy="3" stdDeviation="4" flood-color="#1E2A5A" flood-opacity="0.08"/></filter>
<linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FCFBFF"/><stop offset="1" stop-color="#F6F8FE"/></linearGradient>
</defs>
<rect width="{W}" height="{H}" fill="url(#bg)"/>
{chr(10).join(parts)}
</svg>
'''
OUT.write_text(svg, encoding="utf-8")
print(f"wrote {OUT} ({len(svg)} bytes)")
