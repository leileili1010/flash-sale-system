# 5. A database sweep for payment timeouts

- **Status:** Accepted
- **Date:** 2026-11-12

## Context

A reserved unit must return to stock if the customer does not pay within the payment window
(10 minutes by default). The reference design scheduled a RocketMQ delayed message per order.
Kafka has no per-message delay, and a delayed message is also a second copy of state that can
disagree with the database: the reference listener restocked every unpaid order, including
failed orders that never held a unit, which inflated stock.

## Decision

Store `expires_at` on each order and close expired orders with a scheduled sweep every 5 seconds:

```sql
UPDATE orders SET status = 'CLOSED', closed_at = now()
WHERE id IN (SELECT id FROM orders
             WHERE status = 'PENDING_PAYMENT' AND expires_at <= now()
             ORDER BY expires_at LIMIT 500
             FOR UPDATE SKIP LOCKED)
  AND status = 'PENDING_PAYMENT'
RETURNING order_no, drop_id, user_id;
```

Only the returned rows are restocked (`locked -> available` in the same transaction, then the
idempotent `release.lua` in Redis after commit). A partial index on `expires_at WHERE status =
'PENDING_PAYMENT'` keeps the scan small. ShedLock (JDBC, database clock) runs the job on one
instance at a time.

## Consequences

- The database stays the single source of truth; no timer state can drift from it.
- Releases happen at most 5 seconds late, which is invisible to shoppers. The frontend shows the
  reservation as expired as soon as its own timer reaches zero.
- Payment and expiry are both compare-and-set updates on `status`, so exactly one can win.
- `SKIP LOCKED` lets the sweep run alongside payments without waiting on locked rows.
