-- KEYS[1] : 库存 key
-- ARGV[1] : 回滚数量
local exist = redis.call('exists', KEYS[1])
if exist == 0 then
    return 0   -- key 不存在，跳过（防凭空造库存）
end
redis.call('incrby', KEYS[1], tonumber(ARGV[1]))
return 1