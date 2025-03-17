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
 * todo 2. 重写更新逻辑 cache-aside 先查数据库然后删除缓存，缓存重构过程中需要使用互斥锁
 *      5. 重写分页查询逻辑，缓存查询
 *      6. 布隆过滤应该抓取所有数据，同时新增数据时，必须将新增数据加入到布隆过滤
 *
 * done 1. redis redisson 实现分布式布隆过滤方案
 *      3. 编写切面增强。该增强在 @Cacheable("value = hot*") 切点处统计 hotkey，且 hotkey 满组一定条件时，将 hotkey 写入 caffeine。
 *      0. 厘清 hotkey 查询流程
 */
public class PictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PictureBackendApplication.class, args);
    }
}
