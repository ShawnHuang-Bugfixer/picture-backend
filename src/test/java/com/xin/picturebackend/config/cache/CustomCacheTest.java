package com.xin.picturebackend.config.cache;

import com.xin.picturebackend.service.TrackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 测试基于 spring cache 的自定义缓存逻辑是否生效。
 *
 * @author 黄兴鑫
 * @since 2025/3/15 10:01
 */

@SpringBootTest
public class CustomCacheTest {
    @Resource
    private TrackService trackService;

    @Resource
    private CacheManager multiLevelCacheManger;

    @Resource
    private RedisCacheManager redisCacheManager;

    @Resource
    private CaffeineCacheManager caffeineCacheManager;

    @BeforeEach
    void clearCache() {
        // 清空缓存保证测试隔离性
        Cache cache = multiLevelCacheManger.getCache("hotkey");
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void testTrackCache() {
        // 第一次调用 - 应执行方法并写入缓存
        String result1 = trackService.getTrack(1);
        assertEquals("Track Data 1", result1);

        // 验证缓存是否写入
        Cache cache = multiLevelCacheManger.getCache("hotkey");
        assertNotNull(cache);
//        assertEquals(result1, cache.get("picture:vo:1").get());

        // 获取缓存中的所有键并转换为字符串
        com.github.benmanes.caffeine.cache.Cache nativeCache = (com.github.benmanes.caffeine.cache.Cache) cache.getNativeCache();
        Set keys = nativeCache.asMap().keySet();

        String keyString = String.join(", ", keys.stream().map(Object::toString).toList());

        System.out.println("Cache Keys: " + keyString);

        // 第二次调用 - 应直接从缓存获取
        System.out.println(trackService.getTrack(1));
    }

    @Test
    void testCustomTTL() {
        trackService.getTrack(1);
        Collection<String> cacheNames = redisCacheManager.getCacheNames();
        for (String cacheName : cacheNames) {
            System.out.println(cacheName);
            Cache cache = redisCacheManager.getCache(cacheName);
            if (cache == null) return;
            Cache.ValueWrapper valueWrapper = cache.get("picture:vo:1");
            System.out.println(valueWrapper.get());
        }
    }

    @Test
    void testFromCaffeine() {
        trackService.getTrackFromCaffeine(1);
        Collection<String> cacheNames = caffeineCacheManager.getCacheNames();
        for (String cacheName : cacheNames) {
            System.out.println(cacheName);
            Cache cache = caffeineCacheManager.getCache(cacheName);
            if (cache == null) return;
            Cache.ValueWrapper valueWrapper = cache.get("picture:vo:1");
            System.out.println(valueWrapper);
//            System.out.println(valueWrapper.get());
        }
    }
}
