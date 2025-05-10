package com.xin.picturebackend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.util.concurrent.RateLimiter;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 规定限流粒度为 ip + 方法名，并使用 Guava 令牌桶限流算法进行限流。
 * 使用 Caffeine 存储不同限流粒度及其对应的 Guava 限流器。
 *
 * @author 黄兴鑫
 * @since 2025/5/8 16:38
 */
@SuppressWarnings("UnstableApiUsage")
@Service
public class RateLimiterService {

    private Cache<String, RateLimiter> limiterCache;

    @Value("${app.rateLimiter.defaultPermitsPerSecond}")
    @Getter
    private volatile double defaultPermitsPerSecond;

    @Value("${app.rateLimiter.expireAfterAccess}")
    private volatile int expireAfterAccess;

    @Value("${app.rateLimiter.maximumSize}")
    private volatile int maximumSize;

    @PostConstruct
    public void init() {
        limiterCache = Caffeine.newBuilder()
                .expireAfterAccess(expireAfterAccess, TimeUnit.SECONDS)
                .maximumSize(maximumSize)
                .build();
    }

    /**
     * 获取限流器，使用 IP + 方法名 作为 Key。
     * 创建时使用 defaultPermitsPerSecond。
     */
    public RateLimiter getRateLimiter(String key) {
        return limiterCache.get(key, k -> RateLimiter.create(defaultPermitsPerSecond));
    }

    /**
     * 更新全局限流速率常量 + 热更新缓存中已有的限流器速率。
     */
    public synchronized void updateDefaultPermitsPerSecond(double newPermitsPerSecond) {
        this.defaultPermitsPerSecond = newPermitsPerSecond;

        // 热更新：更新缓存中所有 RateLimiter 的速率
        for (Map.Entry<String, RateLimiter> entry : limiterCache.asMap().entrySet()) {
            RateLimiter limiter = entry.getValue();
            limiter.setRate(newPermitsPerSecond);
        }
    }

    /**
     * 可选：清空缓存。
     */
    public void clearCache() {
        limiterCache.invalidateAll();
    }
}
