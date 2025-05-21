package com.xin.picturebackend.config;

/**
 * redisson 配置
 *
 * @author 黄兴鑫
 * @since 2025/3/15 9:46
 */

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    private final RedisProperties redisProperties;

    public RedissonConfig(RedisProperties redisProperties) {
        this.redisProperties = redisProperties;
    }

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setDatabase(redisProperties.getDatabase())
                .setTimeout(Math.toIntExact(redisProperties.getTimeout().toMillis()))
                .setPassword(redisProperties.getPassword());
        return Redisson.create(config);
    }
}