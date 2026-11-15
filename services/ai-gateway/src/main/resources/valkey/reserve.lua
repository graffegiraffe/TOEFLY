-- Atomic check-and-take over every dimension of one model (docs/06-ai-agent.md §2.5.1).
-- KEYS[1..n]  buckets aiq:{provider}:{model}:{dim}; KEYS[n+1] the 429 pause aiq:{provider}:{model}:block
-- ARGV[1]     number of dimensions n
-- ARGV per dimension i (offset 2 + (i-1)*4): capacity, windowMs, need, floor
-- Returns {1, 0} granted; {0, waitMs} wait; {-1, -1} never fits for this priority.
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local n = tonumber(ARGV[1])
local blocked = redis.call('PTTL', KEYS[n + 1])
if blocked > 0 then
  return {0, blocked}
end
local tokens = {}
local wait = 0
for i = 1, n do
  local base = 2 + (i - 1) * 4
  local cap = tonumber(ARGV[base])
  local window = tonumber(ARGV[base + 1])
  local need = tonumber(ARGV[base + 2])
  local floor = tonumber(ARGV[base + 3])
  if need + floor > cap then
    return {-1, -1}
  end
  local rate = cap / window
  local data = redis.call('HMGET', KEYS[i], 'tokens', 'updatedAt')
  local t = tonumber(data[1])
  local u = tonumber(data[2])
  if t == nil or u == nil then
    t = cap
    u = now
  end
  t = math.min(cap, t + math.max(0, now - u) * rate)
  tokens[i] = t
  if t < need + floor then
    local w = math.ceil((need + floor - t) / rate)
    if w > wait then
      wait = w
    end
  end
end
for i = 1, n do
  local base = 2 + (i - 1) * 4
  local window = tonumber(ARGV[base + 1])
  local take = 0
  if wait == 0 then
    take = tonumber(ARGV[base + 2])
  end
  redis.call('HSET', KEYS[i], 'tokens', tostring(tokens[i] - take), 'updatedAt', tostring(now))
  redis.call('PEXPIRE', KEYS[i], window * 2)
end
if wait > 0 then
  return {0, wait}
end
return {1, 0}
