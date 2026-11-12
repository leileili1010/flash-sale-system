# 4. Kafka for asynchronous order creation

- **Status:** Accepted
- **Date:** 2026-11-12

## Context

The reference design published order requests to RocketMQ, used a RocketMQ delayed message
for payment timeouts, and a second message for "payment completed". The consumer was not
idempotent and the publish step had no compensation if it failed after Redis stock was
taken.

## Decision

- **Apache Kafka** carries `OrderRequestedEvent` on `orders.requested` (6 partitions). It is
  free, widely used, and runs as a single KRaft node locally.
- **The order number is assigned before publishing** (Snowflake id) and is the idempotency
  key: the consumer claims it with `INSERT ... ON CONFLICT DO NOTHING`.
- **Messages are keyed by drop id.** One drop's orders go to one partition and are applied by
  one consumer at a time, so the drop's stock row is not contended by parallel consumers.
  Throughput per drop is bounded by stock, not traffic, because Redis already filtered
  requests down to winners.
- **The purchase request waits for the broker acknowledgement** (`acks=all`, idempotent
  producer, 5 s timeout). If the event cannot be published, the Redis reservation is released
  and the client receives `503`, so no unit is silently lost.
- **Failures:** three retries, then the record goes to `orders.requested.DLT` with its
  original bytes. Malformed records skip the retries.
- **Payment completion does not use a message.** Marking an order paid and moving its unit
  from locked to sold touch the same database, so they happen in one transaction.
- **Payment timeouts do not use delayed messages** (Kafka has none). A scheduled sweep of an
  indexed `expires_at` column replaces them; see the payment milestone.

## Consequences

- The purchase endpoint touches only Redis and Kafka, so its latency does not depend on
  database load.
- Clients poll `GET /api/orders/{orderNo}` until the order leaves `QUEUED`; it is typically
  persisted within milliseconds.
- Redis corrections after rare consumer outcomes (database sold out, duplicate live order)
  happen after the database commit. If such a correction fails, the reconciliation job
  repairs the drift.
