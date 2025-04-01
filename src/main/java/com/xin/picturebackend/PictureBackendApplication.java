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
    // todo 这里需要请求后端获取权限列表。请求参数（spaceId， loginUserId， pictureId）
// todo 修改为新的权限
// todo 定义接口获取新的权限列表
    // todo 修改为自定义权限
// todo 定义接口获取新的权限列表,
// todo pictureVO 中需要包含 spaceId，spaceType，userIdOfPIc
    public static void main(String[] args) {
        SpringApplication.run(PictureBackendApplication.class, args);
    }
}
