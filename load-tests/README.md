# Load tests

[k6](https://k6.io) scenarios that reproduce a drop opening: every virtual user is an
authenticated shopper who presses **Buy** at the same instant.

| Script | What it does | Pass criteria |
|---|---|---|
| `spike.js` | `SHOPPERS` users race for a `STOCK`-unit drop through the real purchase path (rate limit, Redis Lua reservation, Kafka, PostgreSQL). | Exactly `STOCK` orders exist afterwards; nothing is oversold; purchase p95 < 1 s |
| `naive.js` | The same race against a deliberately naive read-then-write endpoint, the approach this project replaced. | None. It exists to show the oversell (its `no oversell` check is expected to fail). |

Both scripts create their own drop and, for `spike.js`, mint one JWT per virtual user through
benchmark-only helpers, so they need the backend running with the **`benchmark` profile**.
Never enable that profile in a shared environment: its helpers are unauthenticated.

## Running

Run everything inside Docker's Linux network. On Windows and macOS, a burst of thousands of
simultaneous connections from the host overflows the host OS's listen backlog and produces
connection errors that have nothing to do with the application.

```sh
# 1. Infrastructure, the backend (benchmark profile) and Prometheus + Grafana
SPRING_PROFILES_ACTIVE=benchmark docker compose -f infra/docker-compose.yml \
  --profile app --profile monitoring up -d --build --wait

# 2. Warm the JVM once (results discarded), then run the scenarios
./load-tests/run.sh spike 500 20
./load-tests/run.sh spike 2000 100
./load-tests/run.sh spike 5000 100
./load-tests/run.sh naive 2000 100
```

Watch the run live on the Grafana dashboard at http://localhost:3000. Each run prints a
summary and writes the full k6 JSON to `load-tests/results/` (git-ignored).

Results and analysis: [`docs/performance.md`](../docs/performance.md).
