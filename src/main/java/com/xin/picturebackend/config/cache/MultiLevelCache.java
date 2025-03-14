package com.xin.picturebackend.config.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.cache.RedisCache;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * 实现 Cache 接口，封装 CaffeineCache 和 RedisCache 以实现热点 key 多重缓存逻辑。
 */
public class MultiLevelCache implements Cache {
    private final String name;
    private final CaffeineCache caffeineCache;
    private final RedisCache redisCache;
    private final Executor asyncExecutor;

    public MultiLevelCache(String cacheName, CaffeineCache caffeineCache, RedisCache redisCache, Executor asyncExecutor) {
        this.name = cacheName;
        this.caffeineCache = caffeineCache;
        this.redisCache = redisCache;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * 实现 hotkey 多级缓存查询逻辑
     *
     * @param key 缓存键
     * @return 返回 缓存值
     */
    @Override
    public ValueWrapper get(Object key) {
        // 1. 查询Caffeine
        ValueWrapper value = caffeineCache.get(key);
        if (value != null) {
            return value;
        }

        // 2. 查询Redis
        ValueWrapper redisValue = redisCache.get(key);
        if (redisValue != null) {
            // fixme 只查询数据，caffeine 存放的热点数据单独控制
            return redisValue;
        }
        return null;
    }

    /**
     * 获取缓存名称
     * 调用时机：缓存管理、日志打印等需要标识缓存实例时
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * 返回底层原生缓存（如Caffeine或Redis的对象）,这里只返回 CaffeineCache
     * 调用时机：需要直接操作底层缓存的高级功能时
     */
    @Override
    public Object getNativeCache() {
        return caffeineCache.getNativeCache();
    }

    /**
     * 多级缓存查询（带类型检查）
     * 调用时机：@Cacheable注解中明确指定返回值类型时
     */
    @Override
    public <T> T get(Object key, Class<T> type) {
        // 1. 查询Caffeine
        T caffeineValue = caffeineCache.get(key, type);
        if (caffeineValue != null) {
            return caffeineValue;
        }

        // 2. 查询Redis并检查类型
        ValueWrapper wrapper = redisCache.get(key);
        if (wrapper != null && type.isInstance(wrapper.get())) {
            T redisValue = type.cast(wrapper.get());
            asyncExecutor.execute(() -> caffeineCache.put(key, redisValue)); // 异步回填
            return redisValue;
        }
        return null;
    }

    /**
     * 缓存加载逻辑（防止缓存穿透）
     * 调用时机：@Cacheable(sync=true)启用同步加载时
     */
    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        try {
            // 1. 尝试从缓存获取
            T value = (T) this.get(key);
            if (value != null) return value;

            // 2. 双重检查锁防止并发重复加载
            synchronized (this) {
                value = (T) this.get(key);
                if (value == null) {
                    value = valueLoader.call();      // 实际加载数据
                    this.put(key, value);            // 写入多级缓存
                }
            }
            return value;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    /**
     * 写入多级缓存
     * 调用时机：@CachePut注解方法执行后或显式更新缓存时
     */
    @Override
    public void put(Object key, Object value) {
        redisCache.put(key, value);           // 同步写Redis保证持久化
//        caffeineCache.put(key, value); // fixme 写入热点数据使用单独逻辑控制
    }

    /**
     * 删除指定键的缓存
     * 调用时机：@CacheEvict注解触发或显式删除时
     */
    @Override
    public void evict(Object key) {
        caffeineCache.evict(key);  // 立即移除本地缓存
        redisCache.evict(key);     // 同步移除Redis缓存
    }

    /**
     * 清空所有缓存
     * 调用时机：@CacheEvict(allEntries=true)时
     */
    @Override
    public void clear() {
        caffeineCache.clear();  // 清空本地缓存
        redisCache.clear();     // 清空Redis缓存
    }
}