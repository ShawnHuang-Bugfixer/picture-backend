package com.xin.picturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.xin.picturebackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true) // 获取代理对象
@EnableScheduling
@EnableAsync

public class PictureBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(PictureBackendApplication.class, args);
    }
}
