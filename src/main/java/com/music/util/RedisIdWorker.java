package com.music.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

//全局唯一ID
@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP=1794137310L;//2026-11-8
    private static final long COUNT_BITS=32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix){
        //生成时间戳
          LocalDateTime now = LocalDateTime.now();
          long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
          long timestamp = nowSecond-BEGIN_TIMESTAMP;
        //生成序列号,每天使用同一个key
        DateTimeFormatter DataTimeFormatter = null;
        String date = now.format(DataTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //拼接
        return timestamp << COUNT_BITS | count;
    }

}
