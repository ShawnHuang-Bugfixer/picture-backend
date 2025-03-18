package com.xin.picturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.xin.picturebackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true) // 获取代理对象
@EnableScheduling
/**
 * todo
 *      6. 布隆过滤应该抓取所有数据，同时新增数据时，必须将新增数据加入到布隆过滤
 */
public class PictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PictureBackendApplication.class, args);
    }
}
