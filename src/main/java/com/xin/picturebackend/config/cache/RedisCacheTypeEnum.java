package com.xin.picturebackend.config.cache;

import lombok.Getter;
import org.springframework.data.redis.cache.CacheKeyPrefix;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 配置不同缓存命名对应的缓存规则
 */
@Getter
public enum RedisCacheTypeEnum {
    // 默认缓存，过期时间 1 小时，JSON 序列化，启用前缀，不缓存 null
    DEFAULT("defaultCache",
            Duration.ofHours(1),
            false,
            true,
            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()),
            RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer())),

    HOTPictureKEY("pictureHotKey",
            Duration.ofMinutes(60 * 24),
            false,
            true,
            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()),
            RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
    private final String cacheName;
    private final RedisCacheConfiguration cacheConfig;

    /**
     * 枚举构造方法
     *
     * @param cacheName       缓存名称
     * @param ttl             过期时间
     * @param usePrefix       是否启用 Key 前缀
     * @param cacheNull       是否缓存 null 值
     * @param keySerializer   Key 的序列化方式
     * @param valueSerializer Value 的序列化方式
     */
    RedisCacheTypeEnum(String cacheName, Duration ttl, boolean usePrefix, boolean cacheNull,
                       RedisSerializationContext.SerializationPair<String> keySerializer,
                       RedisSerializationContext.SerializationPair<?> valueSerializer) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(keySerializer)
                .serializeValuesWith(valueSerializer);

        if (!usePrefix) {
            config = config.disableKeyPrefix(); // 禁用 Key 前缀
        } else {
            config = config.computePrefixWith(CacheKeyPrefix.simple()); // 默认前缀：cacheName::key
        }

        if (!cacheNull) {
            config = config.disableCachingNullValues(); // 不缓存 null
        }

        this.cacheName = cacheName;
        this.cacheConfig = config;
    }

    /**
     * 生成 Map<String, RedisCacheConfiguration>，用于 RedisCacheManager
     */
    public static Map<String, RedisCacheConfiguration> getCacheConfigurations() {
        return Arrays.stream(values())
                .collect(Collectors.toMap(RedisCacheTypeEnum::getCacheName, RedisCacheTypeEnum::getCacheConfig));
    }

    /**
     * 获取所有缓存名称数组
     */
    public static String[] getCacheNames() {
        return Arrays.stream(values())
                .map(RedisCacheTypeEnum::getCacheName)
                .toArray(String[]::new);
    }
}
