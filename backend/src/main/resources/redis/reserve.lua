-- Atomically reserves one unit of a drop for one customer.
--
-- Redis runs a script without interleaving other commands, so the release-window check,
-- the one-per-customer check, the stock check, the decrement and the buyer registration
-- all happen as a single indivisible step. No two requests can both take the last unit,
-- and a customer cannot slip in a second reservation between the check and the write.
--
-- KEYS[1] stock counter   KEYS[2] buyer set   KEYS[3] metadata hash (start, end in epoch ms)
-- ARGV[1] user id         ARGV[2] current time in epoch ms
--
-- Returns {status, remaining}:
--   0 reserved, 1 sold out, 2 already purchased, 3 not started, 4 ended, 5 not loaded

local stock = redis.call('GET', KEYS[1])
if not stock then
  return {5, 0}
end
stock = tonumber(stock)

local window = redis.call('HMGET', KEYS[3], 'start', 'end')
local now = tonumber(ARGV[2])
if not window[1] or not window[2] then
  return {5, stock}
end
if now < tonumber(window[1]) then
  return {3, stock}
end
if now >= tonumber(window[2]) then
  return {4, stock}
end

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
  return {2, stock}
end
if stock <= 0 then
  return {1, 0}
end

redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
return {0, stock - 1}
