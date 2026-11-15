-- Returns (or takes) the difference between the reservation and the actual usage, capped by the capacity.
-- KEYS[i] buckets; ARGV[i*2-1] capacity, ARGV[i*2] delta (positive returns units)
for i = 1, #KEYS do
  local cap = tonumber(ARGV[i * 2 - 1])
  local delta = tonumber(ARGV[i * 2])
  local t = tonumber(redis.call('HGET', KEYS[i], 'tokens'))
  if t ~= nil then
    redis.call('HSET', KEYS[i], 'tokens', tostring(math.min(cap, t + delta)))
  end
end
return 1
