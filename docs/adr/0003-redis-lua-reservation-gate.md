# 3. Redis Lua script as the reservation gate

- **Status:** Accepted
- **Date:** 2026-11-12

## Context

At release time thousands of shoppers request the same few units within seconds. Sending
every request to PostgreSQL would serialize them on one hot row and exhaust the
connection pool long before stock runs out.

The reference design already decremented a Redis counter with a Lua script, but it
checked the one-per-customer limit separately (the check was later disabled), registered
buyers only after asynchronous order creation, and overwrote the Redis counter from the
database on every startup.

## Decision

A single Lua script (`redis/reserve.lua`) performs, atomically:

1. release-window check (the server's clock is passed in, so time handling is testable),
2. one-per-customer check against the drop's buyer set,
3. stock check, decrement, and buyer registration.

A companion script (`redis/release.lua`) returns a unit only if the customer is removed
from the buyer set, which makes releases idempotent.

Preheating loads each published, not-yet-ended drop with `SET NX`: the counter is never
overwritten once it exists, because Redis then holds reservations the database has not
seen yet. Buyers with live orders are re-added from PostgreSQL, so a Redis data loss
cannot let a customer buy twice. All of a drop's keys share a hash tag (`drop:{42}:*`) so
the multi-key scripts stay valid on Redis Cluster.

Rate limiting uses two token buckets: an in-memory global bucket per instance (load
shedding without creating a new hot key) and a Redis-backed per-customer bucket shared by
all instances (Bucket4j).

## Consequences

- PostgreSQL receives roughly one write per unit of stock, independent of traffic.
- Redis is an optimization, not the source of truth. If it loses data mid-drop, the
  database constraints from ADR 2 still prevent overselling. The cost is that some
  customers win a Redis reservation and then get a failed order.
- Displayed stock comes from the Redis counter, so it reflects reservations immediately.
