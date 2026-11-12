# Flash Sale System

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

## Roadmap

- [x] Repository setup
- [x] Local infrastructure (PostgreSQL, Redis, Kafka)
- [ ] Backend foundation, catalog, and authentication
- [ ] Inventory reservation and rate limiting
- [ ] Asynchronous order pipeline, payment, and expiry
- [ ] Concurrency test suite and CI
- [ ] Frontend shop and admin console
- [ ] Load testing, architecture docs, and performance report

## License

Licensed under the [Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for
attribution.
