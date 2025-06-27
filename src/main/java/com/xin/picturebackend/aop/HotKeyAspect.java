package com.xin.picturebackend.aop;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;


@Aspect
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HotKeyAspect {

    @Value("${app.hotkey.detect.threshold}")
    private long HOT_THRESHOLD;

    @Value("${app.hotkey.detect.maxCapacity}")
    private int MAX_CAPACITY;

    @Value("${app.hotkey.detect.expireSec}")
    private long EXPIRE_SECONDS;

    private static final long SCAN_INTERVAL_TIME = 5000;

    @Resource
    private CacheManager caffeineCacheManager;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    // 用于统计访问次数的本地缓存（代替原来的 ConcurrentHashMap）
    private Cache<String, Counter> accessCounterCache;

    @PostConstruct
    public void init() {
        this.accessCounterCache = Caffeine.newBuilder()
                .expireAfterAccess(EXPIRE_SECONDS, TimeUnit.SECONDS)
                .maximumSize(MAX_CAPACITY)
                .build();
    }

    @Scheduled(fixedRate = SCAN_INTERVAL_TIME)
    public void detectHotKeys() {
        accessCounterCache.asMap().forEach((key, counter) -> {
            long count = counter.getAdder().sumThenReset();
            if (count > HOT_THRESHOLD) {
                log.debug("hotkey appear! key :{}, count: {} in 5 seconds", key, count);
                promoteToCaffeine(counter.getCacheNames(), key);
            }
        });
    }

    @Async("cacheWarmupExecutor")
    public void promoteToCaffeine(String[] cacheNames, String key) {
        for (String cacheName : cacheNames) {
            log.debug("put hot key: {} into caffeine: {}", key, cacheName);
            org.springframework.cache.Cache cache = caffeineCacheManager.getCache(cacheName);
            if (cache != null) {
                org.springframework.cache.Cache.ValueWrapper valueWrapper = cache.get(key);
                if (valueWrapper == null) {
                    Object value = redisTemplate.opsForValue().get(key);
                    if (value != null) cache.put(key, value);
                }
            }
        }
    }

    @Around("@annotation(org.springframework.cache.annotation.Cacheable)")
    public Object countAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Cacheable cacheable = method.getAnnotation(Cacheable.class);
        String cacheManagerName = cacheable.cacheManager();

        // 仅当使用自定义多级缓存时，才统计 key 的频率。
        if (!"multiLevelCacheManger".equals(cacheManagerName)) {
            return joinPoint.proceed();
        }

        // 要求 Spring Cache 的缓存名称 value 必须包含 hot 关键字。
        String[] cacheNames = cacheable.value();
        for (String cacheName : cacheNames) {
            if (!cacheName.toLowerCase().contains("hot")) {
                return joinPoint.proceed();
            }
        }

        // 利用 Spring EPL 拼接真实的缓存 key (Redis 和 Caffeine 中保持一致)
        // 此项目中对图片详情进行缓存，利用图片 id 构造缓存 key。
        String keyExpression = cacheable.key();
        SpelExpressionParser parser = new SpelExpressionParser();
        Expression expression = parser.parseExpression(keyExpression);
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] parameterNames = signature.getParameterNames();

        for (int i = 0; i < parameterNames.length; i++) {
            if (parameterNames[i].toLowerCase().contains("id")) {
                context.setVariable(parameterNames[i], joinPoint.getArgs()[i]);
                break;
            }
        }

        String realKey = expression.getValue(context, String.class);

        // 使用 caffeine 缓存统计访问
        accessCounterCache.asMap().compute(realKey, (k, existing) -> {
            if (existing == null) {
                Counter newCounter = new Counter(cacheNames);
                newCounter.increment();
                return newCounter;
            } else {
                existing.increment();
                return existing;
            }
        });

        return joinPoint.proceed();
    }

    @Getter
    static class Counter {
        private final LongAdder adder = new LongAdder();
        private final String[] cacheNames;

        public Counter(String[] cacheNames) {
            this.cacheNames = cacheNames;
        }

        public void increment() {
            adder.increment();
        }
    }
}

