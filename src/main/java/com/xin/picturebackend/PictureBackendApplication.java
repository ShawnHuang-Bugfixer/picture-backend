package com.xin.picturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * todo
 *      1. 增加功能
 *          1.1 使用基础属性搜索 done
 *          1.2 以图搜图
 *              测试 bing 图库搜索 api 调用
 *          1.3 使用颜色搜图
 *          1.4 图片批量管理
 */
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
