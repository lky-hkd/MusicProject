package com.music.util;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {

    // Spring 自动注入的 RedisTemplate（key 用 String 序列化，value 用 JSON）
    @Autowired
    private RedisTemplate<String,Object> redisTemplate;

    /**
     * 设置 key-value，并指定过期时间
     * @param key   键
     * @param value 值
     * @param timeout 过期时间（秒），如果 <=0 表示不过期
     */
    public void set(String key,String value,long timeout){
        if(timeout >0){
            redisTemplate.opsForValue().set(key,value,timeout, TimeUnit.SECONDS);
        }else{
            redisTemplate.opsForValue().set(key,value);
        }
    }
//    设置 key-value（不过期）
    //public void set(String key, int total, Object value){
//        redisTemplate.opsForValue().set(key,value);
//    }


    public Object get(String key){
        return redisTemplate.opsForValue().get(key);
    }

    public String getString(String key){
        Object o = redisTemplate.opsForValue().get(key);
        return o==null?"":o.toString();
    }

    public Integer getInteger(String key){
        Object o = redisTemplate.opsForValue().get(key);
        if(o==null){ return null;}
        if(o instanceof Integer){ return (Integer)o;}
        try{
            return Integer.parseInt(o.toString());
        }catch(NumberFormatException e){
            return null;
        }
    }

    public Boolean delete(String key){
        return redisTemplate.delete(key);
    }

    public Boolean hasKey(String key){
        return redisTemplate.hasKey(key);
    }

    public Boolean expire(String key,long time){
        return redisTemplate.expire(key,time,TimeUnit.SECONDS);
    }

//    获取过期时间（秒）,return -1 表示永久，-2 表示不存在
    public Long getExpire(String key){
        return redisTemplate.getExpire(key,TimeUnit.SECONDS);
    }

    /**
     * 自增（可用于点赞、计数器、库存预扣）
     * @param key   键
     * @param delta 增量（正数增加，负数减少）
     * @return 自增后的值
     */
    public Long increment(String key,long delta){
        return redisTemplate.opsForValue().increment(key,delta);
    }

//    自减（等价于 increment(key, -1)）
    public Long decrement(String key,long delta){
        return redisTemplate.opsForValue().decrement(key,delta);
    }
}
