package com.xin.picturebackend;

import com.xin.picturebackend.service.TrackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;


import javax.annotation.Resource;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PictureBackendApplicationTests {

    @Resource
    private TrackService trackService;

    @Resource
    private CacheManager multiLevelCacheManger;

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
        assertEquals(result1, cache.get("picture:vo:1").get());

        // 获取缓存中的所有键并转换为字符串
        com.github.benmanes.caffeine.cache.Cache nativeCache = (com.github.benmanes.caffeine.cache.Cache) cache.getNativeCache();
        Set keys = nativeCache.asMap().keySet();

        String keyString = String.join(", ", keys.stream().map(Object::toString).toList());

        System.out.println("Cache Keys: " + keyString);

        // 第二次调用 - 应直接从缓存获取
        String result2 = trackService.getTrack(1);
        assertEquals("Track Data 1", result2);
    }

    @Test
    void testCacheEviction() {
        // 测试缓存失效场景（如果配置了TTL）
        trackService.getTrack(2);
        Cache cache = multiLevelCacheManger.getCache("trackCache");
        assertNotNull(cache.get("track:2"));

        // 模拟时间流逝或手动清除缓存
        cache.evict("track:2");
        assertNull(cache.get("track:2"));
    }
}
