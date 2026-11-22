package com.music;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAspectJAutoProxy(exposeProxy = true)
@SpringBootApplication
@EnableRabbit
@EnableScheduling
@MapperScan("com.music.mapper")
public class MusicPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(MusicPlatformApplication.class, args);
    }
}
