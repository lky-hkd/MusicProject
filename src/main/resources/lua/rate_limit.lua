-- KEYS[1] : 限流 key
-- ARGV[1] : 过期秒数
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
end
return count