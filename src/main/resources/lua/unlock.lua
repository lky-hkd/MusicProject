-- 获取锁中的标识，相同放锁
if(redis.call('GET',KEYS[1] == ARGV[1])) then
    return redis.call('DEL',KEYS[1])
end

return 0