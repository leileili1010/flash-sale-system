# 2. PostgreSQL as the system of record

- **Status:** Accepted
- **Date:** 2026-11-12

## Context

The reference design used MySQL with MyBatis. During a drop, Redis absorbs the request
spike and only a number of requests close to the available stock reaches the database, so
raw write throughput is not the deciding factor. What matters is how easily the database
can *guarantee* correctness when messages are duplicated, delayed, or processed
concurrently.

## Decision

Use PostgreSQL with Flyway-managed migrations, and push correctness rules into the schema:

- `CHECK (available_stock + locked_stock + sold_stock = total_stock)` and non-negative
  checks on every stock bucket, so no code path can oversell or lose a unit.
- A partial unique index on `orders (drop_id, user_id) WHERE status IN ('PENDING_PAYMENT', 'PAID')`,
  allowing exactly one live order per customer per drop while still letting customers retry
  after an order expires or fails.
- A unique `order_no` used with `INSERT ... ON CONFLICT DO NOTHING`, making the Kafka
  consumer idempotent without exception-driven control flow.
- A partial index on `orders (expires_at) WHERE status = 'PENDING_PAYMENT'`, keeping the
  payment-expiry sweep cheap regardless of order history size.
- `UPDATE ... RETURNING` for state transitions, so callers learn which rows changed in one
  round trip.

## Consequences

- PostgreSQL's MVCC creates a new row version for each update. The `drops` row is updated
  once per reservation, so the table uses `fillfactor = 80` to keep those updates HOT
  (heap-only tuple) and cheap to vacuum.
- Schema changes are transactional: a failed migration rolls back completely.
- Spring Data JPA covers ordinary reads and writes; stock and order-state transitions use
  explicit native SQL so their concurrency behavior is obvious when reading the code.
