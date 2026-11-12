# 1. Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-11-12

## Context

This system adapts an existing flash-sale architecture to a modern stack. Many
decisions (message broker, database, rate limiting, timeout handling) trade
simplicity against correctness under load, and the reasoning should stay
visible after the code changes.

## Decision

Significant architectural decisions are recorded as short Architecture Decision
Records in `docs/adr/`, numbered sequentially, using the format: Context,
Decision, Consequences.

## Consequences

Each record is immutable once accepted; a later decision supersedes an earlier
one by referencing it rather than editing it.
