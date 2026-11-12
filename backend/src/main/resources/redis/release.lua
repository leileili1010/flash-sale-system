-- Returns a customer's reserved unit to the pool (failed order creation or payment expiry).
--
-- The stock is incremented only if the customer was actually removed from the buyer set,
-- which makes the release idempotent: a retried or duplicated release cannot inflate stock.
--
-- KEYS[1] stock counter   KEYS[2] buyer set
-- ARGV[1] user id
--
-- Returns the new stock level, or -1 if there was nothing to release.

if redis.call('SREM', KEYS[2], ARGV[1]) == 1 then
  return redis.call('INCR', KEYS[1])
end
return -1
