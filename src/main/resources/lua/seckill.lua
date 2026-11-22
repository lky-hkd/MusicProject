-- 参数说明：
-- KEYS[1] : 库存的 key (例如: product:stock:1)
-- ARGV[1] : 扣减的数量 (例如: 1)

local stock = tonumber(redis.call('get',KEYS[1]))
if not stock then
    return 0
end

local stockNum = tonumber(stock)
local quantity = tonumber(ARGV[1])

-- 如果库存不存在，或者库存不足
if stockNum<quantity then
    return 0
end

-- 扣减库存
redis.call('decrby',KEYS[1],quantity)
return 1


