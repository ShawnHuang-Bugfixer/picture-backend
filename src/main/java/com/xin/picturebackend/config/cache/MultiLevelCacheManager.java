package com.xin.picturebackend.config.cache;

import lombok.NonNull;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
 * 自定义多级缓存管理。构建 value = ‘customCache’ 和自定义多级缓存 MultiLevelCache 的一对一映射。
 * <p>
 * 接收 CaffeineCacheManger 和 RedisCacheManger，从两个 cacheManager 中获取统一的配置信息。
 * 为了避免动态配置 value 值导致使用默认配置对 cache 进行配置，建议 hotkey 先在 RedisCacheManger
 * 中配置缓存信息。
 */
public class MultiLevelCacheManager implements CacheManager {
    private final CaffeineCacheManager caffeineCacheManager;
    private final RedisCacheManager redisCacheManager;
    private final ConcurrentMap<String, MultiLevelCache> caches = new ConcurrentHashMap<>();
    private final Executor asyncExecutor;

    public MultiLevelCacheManager(CaffeineCacheManager caffeineCacheManager,
                                  RedisCacheManager redisCacheManager, Executor asyncExecutor) {
        this.caffeineCacheManager = caffeineCacheManager;
        this.redisCacheManager = redisCacheManager;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    public Cache getCache(@NonNull String name) {
        return caches.computeIfAbsent(name, cacheName ->
                new MultiLevelCache(
                        cacheName,
                        (CaffeineCache) caffeineCacheManager.getCache(cacheName),
                        (RedisCache) redisCacheManager.getCache(cacheName),
                        asyncExecutor
                )
        );
    }

    @Override
    @NonNull
    public Collection<String> getCacheNames() {
        return caches.keySet();
    }
}
