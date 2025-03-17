package com.xin.picturebackend.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.concurrent.TimeUnit;


/**
 * 配置不同的 CacheManager 实例
 *
 * @author 黄兴鑫
 * @since 2025/3/13 11:53
 */
@Configuration
@EnableCaching
public class CacheConfig {
    // caffeine 默认配置
    private static final int CAFFEINE_INT_NUM = 100;
    private static final int CAFFEINE_MAX_SIZE = 1000;
    private static final int TTL_SECONDS = 10 * 60;

    // Caffeine 配置 可以通过 yml 配置代替
    @Bean("caffeineCacheManager")
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(CAFFEINE_INT_NUM)
                .maximumSize(CAFFEINE_MAX_SIZE)
                .expireAfterWrite(TTL_SECONDS, TimeUnit.SECONDS)
                .recordStats());
        return cacheManager;
    }

    // Redis 缓存配置
    @Bean("redisCacheManager")
    @Primary
    public CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(RedisCacheTypeEnum.DEFAULT.getCacheConfig())
                .withInitialCacheConfigurations(RedisCacheTypeEnum.getCacheConfigurations())
                .build();
    }

    @Bean("multiLevelCacheManger")
    public CacheManager multiLevelCacheManger(@Qualifier("caffeineCacheManager") CacheManager caffeineCacheManager,
                                              @Qualifier("redisCacheManager") CacheManager redisCacheManager
                                              ) {
        return new MultiLevelCacheManager((CaffeineCacheManager) caffeineCacheManager, (RedisCacheManager) redisCacheManager);
    }
}
