package com.xin.picturebackend.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 测试基于 spring cache 搭建的 caffeine，redis 的多级缓存
 *
 * @author 黄兴鑫
 * @since 2025/3/13 17:35
 */
@Deprecated
@Service
public class TrackService {
//    @Caching(
//            cacheable = {
//                    @Cacheable(cacheManager = "redisCacheManager", value = "pictureHotVOList", key = "'picture:vo:' + #id"),
//                    @Cacheable(cacheManager = "redisCacheManager", value = "pictureColdVOList", key = "'picture:hotKey:' + #id"),
//            }
//    )
    @Cacheable(cacheManager = "redisCacheManager", value = "pictureHotVOList", key = "'picture:vo:' + #id")
    public String getTrack(int id) {
        // 实际业务逻辑（例如数据库查询）
        return "data from list";
    }

    @Cacheable(cacheManager = "caffeineCacheManager", value = {"cache2", "cache1"}, key = "'picture:vo:' + #id")
    public String getTrackFromCaffeine(int id) {
        return "data from caffeine";
    }
}
