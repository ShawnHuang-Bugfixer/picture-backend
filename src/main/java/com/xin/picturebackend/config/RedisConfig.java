package com.xin.picturebackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 *
 * @author 黄兴鑫
 * @since 2025/3/17 17:07
 */
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // 设置 Key 和 Value 的序列化方式
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());

        return template;
    }

    @Value("${app.script.lua.deleteByRefreshToken.path}") // 假设你的Lua脚本路径配置在配置文件中
    private String deleteByRefreshTokenScriptPath;

    @Value("${app.script.lua.deleteByUserIdToken.path}")
    private String deleteByUserIdScriptPath;

    @Bean(name = "deleteByRefreshTokenScript")
    public RedisScript<Long> deleteByRefreshTokenScript() {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource(deleteByRefreshTokenScriptPath));
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    @Bean(name = "deleteByUserIdScript")
    public RedisScript<Long> deleteByUserIdScript() {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource(deleteByUserIdScriptPath));
        redisScript.setResultType(Long.class);
        return redisScript;
    }

}
