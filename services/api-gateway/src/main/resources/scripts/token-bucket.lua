-- Token bucket of api-gateway (docs/05-api-contracts.md §2.10). KEYS: tokens, timestamp.
-- ARGV: replenish rate per second, burst capacity, now in seconds (fractional), requested tokens.
local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local ttl = math.max(1, math.ceil(capacity / rate * 2))
local tokens = tonumber(redis.call("get", KEYS[1])) or capacity
local refreshed = tonumber(redis.call("get", KEYS[2])) or now

local filled = math.min(capacity, tokens + math.max(0, now - refreshed) * rate)
local allowed = filled >= requested
if allowed then
  filled = filled - requested
end

redis.call("setex", KEYS[1], ttl, tostring(filled))
redis.call("setex", KEYS[2], ttl, tostring(now))
return { allowed and 1 or 0, tostring(filled) }
