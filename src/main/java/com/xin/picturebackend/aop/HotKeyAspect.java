package com.xin.picturebackend.aop;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.cache.Cache;
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

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;


@Aspect
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)



//      测试 @Cacheable value 未指定 hot 能否正确执行逻辑
//      测试数据查询是否正确经过缓存逻辑，
//      测试切面 hotkey 是否生效，数据是否正确存入 caffeine

/**
 * 将一段时间内访问次数超过指定阈值的 key 视为 hotKey 并写入 caffeine
 *
 * @author 黄兴鑫
 * @since 2025/3/15 15:17
 */
public class HotKeyAspect {

    private static final long HOT_THRESHOLD = 100; // 5s 内 key 出现 100 次视为 hotkey
    private static final long MAX_AGE_MINUTES = 30;// 计数器中 key 最大存活时间
    private final ConcurrentMap<String, Counter> keyCounter = new ConcurrentHashMap<>();
    @Resource
    private CacheManager caffeineCacheManager;
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
                log.error("hotkey appear! key :{}, count: {} in {} seconds", entry.getKey(), count, 5);
                String key = entry.getKey();
                String[] cacheNames = counter.getCacheNames();
                promoteToCaffeine(cacheNames, key);
            }
            return false;
        });
    }

    @Async
    public void promoteToCaffeine(String[] cacheNames, String key) {
//        log.error("try to put key :{} into caffeine", key);
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            for (String cacheName : cacheNames) {
                log.error("put hot key: {} into caffeine: {}", key, cacheName);
                Cache cache = caffeineCacheManager.getCache(cacheName);
                if (cache != null) cache.put(key, value);
            }
        }
    }

    /**
     * 热点数据检测 @Cacheable(cacheManger = "", value = "", key = "") 要求 value 必须包含 hot
     */
    @Around("@annotation(org.springframework.cache.annotation.Cacheable)")
    public Object countAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        // 解析缓存名称
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Cacheable cacheable = method.getAnnotation(Cacheable.class);
        String[] cacheNames = cacheable.value();
        for (String cacheName : cacheNames) {
            if (!cacheName.toLowerCase().contains("hot")) {
                return joinPoint.proceed();
            }
        }

        // 解析真实缓存key,参数列表中第一个包含“id”的值拼接为 realKey
        String keyExpression = cacheable.key();
        SpelExpressionParser parser = new SpelExpressionParser();
        Expression expression = parser.parseExpression(keyExpression);
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] parameterNames = signature.getParameterNames(); // 获取参数名称
        for (int i = 0; i < parameterNames.length; i++) {
            if (parameterNames[i].toLowerCase().contains("id")) {
                context.setVariable(parameterNames[i], joinPoint.getArgs()[i]);
                break;
            }
        }
        String realKey = expression.getValue(context, String.class);

        // 更新计数器和访问时间
        keyCounter.computeIfAbsent(realKey, (m) -> new Counter(cacheNames)).increment();

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
