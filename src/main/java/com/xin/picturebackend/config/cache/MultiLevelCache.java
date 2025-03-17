package com.xin.picturebackend.config.cache;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.cache.RedisCache;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * 实现 Cache 接口，封装 CaffeineCache 和 RedisCache 以实现热点 key 多重缓存逻辑。
 */
@Slf4j
public class MultiLevelCache implements Cache {
    private final String name;
    private final CaffeineCache caffeineCache;
    private final RedisCache redisCache;
    private final RedissonClient redissonClient;
    private static final long lockWaitTime = 3;                 // 锁等待时间（秒）
    private static final long lockLeaseTime = 10;               // 锁自动释放时间（秒）

    public MultiLevelCache(String cacheName, CaffeineCache caffeineCache, RedisCache redisCache, RedissonClient redissonClient) {
        this.name = cacheName;
        this.caffeineCache = caffeineCache;
        this.redisCache = redisCache;
        this.redissonClient = redissonClient;
    }

    /**
     * 实现 hotkey 多级缓存查询逻辑
     *
     * @param key 缓存键
     * @return 返回 缓存值
     */
    @Override
    public ValueWrapper get(@NonNull Object key) {
        // 1. 查询Caffeine
        ValueWrapper value = caffeineCache.get(key);
        if (value != null) {
//            log.error("get key:{} from caffeine", key);
            return value;
        }

        // 2. 查询Redis
        ValueWrapper redisValue = redisCache.get(key);
        if (redisValue != null) {
//            log.error("get key:{} from redis", key);
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
    public <T> T get(@NonNull Object key, Class<T> type) {
        // 1. 查询Caffeine
        T caffeineValue = caffeineCache.get(key, type);
        if (caffeineValue != null) {
            return caffeineValue;
        }

        // 2. 查询Redis并检查类型
        ValueWrapper wrapper = redisCache.get(key);
        if (wrapper != null && type.isInstance(wrapper.get())) {
            //            asyncExecutor.execute(() -> caffeineCache.put(key, redisValue)); // 异步回填
            return type.cast(wrapper.get());
        }
        return null;
    }

    /**
     * 缓存加载逻辑（防止缓存穿透）
     * 调用时机：@Cacheable(sync=true)启用同步加载时
     */
    @Override
    public <T> T get(@NonNull Object key, @NonNull Callable<T> valueLoader) {
        // 1. 先尝试从本地缓存获取
        ValueWrapper value =  caffeineCache.get(key);
        if (value != null) {
            return (T)value.get();
        }

        // 2. 尝试从 Redis 缓存获取
        value = redisCache.get(key);
        if (value != null) {
            return (T)value.get();
        }

        // 3. 获取分布式锁（按 Key 加锁）
        RLock lock = redissonClient.getLock("lock:" + key.toString());
        try {
            // 3.1 尝试加锁（等待 lockWaitTime 秒，锁持有时间 lockLeaseTime 秒）
            if (lock.tryLock(lockWaitTime, lockLeaseTime, TimeUnit.SECONDS)) {
                try {
                    // 3.2 双重检查 Redis 缓存（防止其他线程已加载）
                    value = redisCache.get(key);
                    if (value != null) {
                        return (T)value.get();
                    }
                    log.error("rebuild redis cache----------------------------------------");
                    // 3.3 执行加载逻辑（如数据库查询）
                    T data = valueLoader.call();

                    // 3.4 更新 Redis 和本地缓存
                    redisCache.put(key, data);
                    return data;
                } finally {
                    lock.unlock();
                }
            } else {
                // 3.5 锁获取失败，等待并重试从 Redis 获取
                while (true) {
                    Thread.sleep(50); // 避免 CPU 忙等
                    value = redisCache.get(key);
                    if (value != null) {
                        return (T)value.get();
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValueRetrievalException(key, valueLoader, e);
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    /**
     * 写入多级缓存
     * 调用时机：@CachePut注解方法执行后或显式更新缓存时
     */
    @Override
    public void put(@NonNull Object key, Object value) {
        redisCache.put(key, value);           // 同步写Redis保证持久化
    }

    /**
     * 删除指定键的缓存
     * 调用时机：@CacheEvict注解触发或显式删除时
     */
    @Override
    public void evict(@NonNull Object key) {
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