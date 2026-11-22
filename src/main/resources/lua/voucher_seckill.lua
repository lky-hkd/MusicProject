-- 秒杀券领取（Redis 预扣库存 + 一人一券）
-- KEYS[1] : 库存 key  seckill:voucher:stock:{voucherId}
-- KEYS[2] : 已领用户集合  seckill:voucher:users:{voucherId}
-- ARGV[1] : userId
-- 返回：1 成功 / 0 售罄 / 2 重复领取 / -1 库存未预热

local stock = redis.call('get', KEYS[1])
if not stock then
    return -1
end

if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return 2
end

if tonumber(stock) <= 0 then
    return 0
end

redis.call('decr', KEYS[1])
redis.call('sadd', KEYS[2], ARGV[1])
return 1
