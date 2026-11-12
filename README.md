# Flash Sale System

[![CI](https://github.com/leileili1010/flash-sale-system/actions/workflows/ci.yml/badge.svg)](https://github.com/leileili1010/flash-sale-system/actions/workflows/ci.yml)

A full-stack, high-concurrency e-commerce platform for limited-edition designer
collectibles and blind boxes. It simulates scheduled product drops where
thousands of shoppers try to buy a small, fixed inventory at the same instant,
and it guarantees that nothing is ever oversold.

> **Status:** under active development. See the roadmap below.

## Highlights

- **Atomic stock reservation in Redis** — a single Lua script checks the release
  window, the one-per-customer limit, and remaining stock, then reserves a unit.
  Only winning requests ever reach the database.
- **Asynchronous order pipeline on Apache Kafka** — idempotent producers,
  idempotent consumers, retries, and a dead-letter topic.
- **Database-level oversell guard** — conditional stock updates and unique
  constraints in PostgreSQL make duplicate or out-of-order messages harmless.
- **Payment timeouts** — unpaid orders expire and release their stock exactly
  once, even when a late payment races the expiry job.
- **Rate limiting** — distributed token buckets protect the purchase endpoint
  during traffic spikes.
- **Measured, not assumed** — Testcontainers concurrency tests and k6 load tests,
  with results in `docs/performance.md`.

## Tech Stack

| Layer          | Technology                                              |
|----------------|---------------------------------------------------------|
| Frontend       | React, TypeScript, Vite, Tailwind CSS, TanStack Query   |
| Backend        | Java 25, Spring Boot 4, Spring Security (JWT)           |
| Data           | PostgreSQL, Flyway, Spring Data JPA                     |
| Cache / stock  | Redis (Lua scripts)                                     |
| Messaging      | Apache Kafka (KRaft)                                    |
| Observability  | Spring Boot Actuator, Micrometer, Prometheus, Grafana   |
| Testing        | JUnit 5, Testcontainers, Vitest, Playwright, k6         |
| Local runtime  | Docker Compose                                          |

## Repository Layout

```
backend/      Spring Boot application
frontend/     React + TypeScript single-page app
infra/        Docker Compose stack and monitoring configuration
load-tests/   k6 load-testing scenarios
docs/         Architecture notes, decision records, performance results
```

## Local Infrastructure

Requires Docker (Docker Desktop, Podman, or Rancher Desktop).

```sh
docker compose -f infra/docker-compose.yml up -d --wait
```

| Service    | Host address            | Notes                                   |
|------------|-------------------------|-----------------------------------------|
| PostgreSQL | `localhost:5433`        | db/user/password: `flashsale`           |
| Redis      | `localhost:6379`        | AOF persistence, no eviction            |
| Kafka      | `localhost:9092`        | KRaft single node, topic auto-create off |
| Kafka UI   | http://localhost:8085   | Browse topics, messages, consumer lag   |

PostgreSQL is published on port 5433 so it does not clash with a locally
installed server. Override any port by copying `infra/.env.example` to
`infra/.env`. Stop the stack with `docker compose -f infra/docker-compose.yml down`
(add `-v` to delete data volumes).

## Running the Backend

Requires JDK 25 and the local infrastructure above.

```sh
cd backend
./mvnw spring-boot:run        # http://localhost:8080
./mvnw verify                 # unit + integration tests (Testcontainers needs Docker)
```

| Endpoint                | Description                                   |
|-------------------------|-----------------------------------------------|
| `GET /api/products`     | Product catalog                               |
| `GET /api/products/{id}`| Product details (Redis-cached)                |
| `GET /api/drops`        | Published drops; `?phase=LIVE\|UPCOMING\|ENDED` |
| `GET /api/drops/{id}`   | Drop details with live phase and stock        |
| `GET /api/time`         | Server time, used to sync drop countdowns     |
| `POST /api/auth/register` | Create an account, returns a JWT            |
| `POST /api/auth/login`  | Exchange credentials for a JWT                |
| `GET /api/auth/me`      | Current user (requires `Authorization: Bearer`) |
| `POST /api/drops/{id}/purchase` | Reserve a unit; `202` with an order number to poll |
| `GET /api/orders/{orderNo}` | Order status: `QUEUED`, `PENDING_PAYMENT`, `PAID`, `CLOSED`, `FAILED` |
| `GET /api/orders`       | The caller's recent orders                    |
| `POST /api/orders/{orderNo}/pay` | Simulated checkout; idempotent       |
| `GET /api/admin/inventory` | Admin: Redis vs. database stock and drift  |
| `POST /api/admin/inventory/{dropId}/resync` | Admin: rebuild a drop's Redis state |
| `GET /actuator/health`  | Liveness and readiness                        |

Catalog endpoints are public; everything else requires a bearer token, and
`/api/admin/**` plus non-health Actuator endpoints require the `ADMIN` role.

Demo accounts (created on startup when `DEMO_SEED_USERS=true`, the local default):

| Role    | Email                 | Password           |
|---------|-----------------------|--------------------|
| Admin   | `admin@fluffle.dev`   | `admin-password`   |
| Shopper | `shopper@fluffle.dev` | `shopper-password` |

Errors use [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details with a
stable `code` field (for example `SOLD_OUT` or `ALREADY_PURCHASED`) that clients can
branch on.

Configuration is read from environment variables with local defaults: `DB_HOST`,
`DB_PORT` (5433), `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `SERVER_PORT`, and `WORKER_ID`
(unique per instance, used for order-number generation), `REDIS_HOST`, `REDIS_PORT`,
`JWT_SECRET` (Base64, at least 32 bytes; a development default is provided),
`JWT_TTL` (default `2h`), `KAFKA_BOOTSTRAP_SERVERS`, `ORDER_PAYMENT_WINDOW` (default `10m`), `ORDER_EXPIRY_SWEEP_INTERVAL` (default `5s`), `CORS_ALLOWED_ORIGINS`, `DEMO_SEED_USERS`, and
`DEMO_SEED_DROPS` (default `true`: creates a demo schedule relative to the current
time whenever no live or upcoming drop exists).

## Running the Frontend

Requires Node.js 24 (see `frontend/.nvmrc`) and the backend running on port 8080.

```sh
cd frontend
npm install
npm run dev        # http://localhost:5173 (proxies /api to the backend)
npm test           # Vitest + Testing Library
npm run build      # type-check and production build
```

Product artwork lives in `frontend/public/images/products/`, one file per product, named as
in the catalog's `image_url`. Replacing a file updates the shop; products without artwork
fall back to a drawn placeholder.

## Roadmap

- [x] Repository setup
- [x] Local infrastructure (PostgreSQL, Redis, Kafka)
- [x] Backend foundation, catalog, and authentication
- [x] Inventory reservation and rate limiting
- [x] Asynchronous order pipeline, payment, and expiry
- [x] Concurrency test suite and CI
- [ ] Frontend shop and admin console
- [ ] Load testing, architecture docs, and performance report

## License

Licensed under the [Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for
attribution.
