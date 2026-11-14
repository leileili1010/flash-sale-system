# 6. Two-tier rate limiting with Bucket4j

- **Status:** Accepted
- **Date:** 2026-11-12

## Context

During a drop, the purchase endpoint must shed load it cannot serve and stop scripted clients
from hammering it, without itself becoming a bottleneck. The reference design used Alibaba
Sentinel with an in-process QPS rule, which only limits each instance and cannot recognise one
customer spreading requests across instances.

## Decision

Two token buckets guard `POST /api/drops/{id}/purchase`:

1. **Global, per instance, in memory** (default 5,000 requests per second). It sheds excess load
   before any Redis call. It is deliberately local: a single shared global bucket would be a hot
   key that every request contends on.
2. **Per customer, in Redis** (default 5 attempts per 10 seconds), using Bucket4j's Lettuce
   compare-and-swap backend. It is shared by every instance, so a bot cannot multiply its
   allowance by spreading requests across servers.

Rejections return `429 RATE_LIMITED` with a `Retry-After` header; the frontend shows a live
"retry in N s" countdown and re-enables the button by itself.

## Consequences

- Global capacity scales with the number of instances.
- The per-customer check costs Redis round trips on the hot path. Folding it into the reservation
  script is a possible optimisation (see `performance.md`).
- The Redis connection for buckets follows the Lettuce connection factory's lifecycle, so it
  survives factory restarts (found by Spring Framework 7's test-context pausing).
