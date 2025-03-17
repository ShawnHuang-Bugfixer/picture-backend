package com.xin.picturebackend.aop;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * hotkey 切面统计
 *
 * @author 黄兴鑫
 * @since 2025/3/15 15:17
 */
@Aspect
@Component
@Slf4j
public class HotKeyAspect {

    private static final long HOT_THRESHOLD = 100; // 5s 内 key 出现 100 次视为 hotkey
    private static final long MAX_AGE_MINUTES = 30;// 计数器中 key 最大存活时间
    private final ConcurrentMap<String, Counter> keyCounter = new ConcurrentHashMap<>();
    @Resource
    private CacheManager multiLevelCacheManger;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Scheduled(fixedRate = 5000)  // 每5秒检测一次
    public void detectHotKeys() {
        long currentTime = System.currentTimeMillis();
        keyCounter.entrySet().removeIf(entry -> {
            Counter counter = entry.getValue();
            // 清理超过30分钟未访问的条目
            if (currentTime - counter.getLastAccessTime() > TimeUnit.MINUTES.toMillis(MAX_AGE_MINUTES)) {
                return true;
            }
            // 获取并重置计数器
            long count = counter.getAdder().sumThenReset();
            if (count > HOT_THRESHOLD) {
                String key = entry.getKey();
                String[] cacheNames = counter.getCacheNames();
                promoteToCaffeine(cacheNames, key);
            }
            return false;
        });
    }

    @Async
    public void promoteToCaffeine(String[] cacheNames, String key) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            for (String cacheName : cacheNames) {
                Cache cache = multiLevelCacheManger.getCache(cacheName);
                if (cache != null) cache.put(key, value);
            }
        }
    }

    /**
     * 热点数据检测 @Cacheable(cacheManger = "", value = "", key = "") 要求 value 必须包含 hot
     */
    @Around("@annotation(cacheable)")
    public Object countAccess(ProceedingJoinPoint joinPoint, Cacheable cacheable) throws Throwable {
        // 解析缓存名称
        String[] cacheNames = cacheable.value();
        for (String cacheName : cacheNames) {
            if (!cacheName.toLowerCase().contains("hot")) return joinPoint.proceed();
        }
        // 解析真实缓存key
        String key = cacheable.key();
        // 更新计数器和访问时间
        keyCounter.computeIfAbsent(key, (m) -> new Counter(cacheNames)).increment();

        return joinPoint.proceed();
    }


    @Getter
    static class Counter {
        private final LongAdder adder = new LongAdder();
        private volatile long lastAccessTime = System.currentTimeMillis();
        private String[] cacheNames;

        public void increment() {
            adder.increment();
            lastAccessTime = System.currentTimeMillis();
        }

        public Counter(String[] cacheNames) {
            this.cacheNames = cacheNames;
        }
    }
}
