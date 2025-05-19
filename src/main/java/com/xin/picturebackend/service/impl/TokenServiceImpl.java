package com.xin.picturebackend.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xin.picturebackend.config.CookiesProperties;
import com.xin.picturebackend.constant.RedisKeyConstant;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.service.TokenService;
import com.xin.picturebackend.token.RefreshToken;
import com.xin.picturebackend.utils.CookieUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author 黄兴鑫
 * @since 2025/4/26 21:00
 */
@Service
@Slf4j
public class TokenServiceImpl implements TokenService {

    @Value("${sa-token.timeout}")
    private Long JWTRedisTTL;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CookiesProperties cookiesProperties;

    @Resource
    private RedisScript<Long> deleteByUserIdScript;

    @Resource
    private RedisScript<Long> deleteByRefreshTokenScript;

//    private static final String LUA_SCRIPT_PATH = "lua/delete_refresh_and_reverse.lua";
//    private static final String DELETE_SCRIPT_PATH = "lua/delete_by_userid.lua";

    @Override
    @Deprecated
    public boolean checkJWTSessionInRedis(Long loginId, String jti) throws BusinessException {
        ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
        String key = RedisKeyConstant.JWT_KEY_PREFIX + loginId;
        String fetchJti = operations.get(key);
        return fetchJti != null && fetchJti.equals(jti);
    }

    @Override
    public RefreshToken checkAndGetRefreshTokenObj(String refreshToken) {
        if (StrUtil.isEmpty(refreshToken)) {
            return null;
        }

        String redisKey = RedisKeyConstant.REFRESH_TOKEN_KEY_PREFIX + refreshToken;
        String refreshTokenJson = stringRedisTemplate.opsForValue().get(redisKey);
        if (StrUtil.isEmpty(refreshTokenJson)) {
            return null;
        }

        RefreshToken tokenObj = JSONUtil.toBean(refreshTokenJson, RefreshToken.class);
        if (tokenObj == null || tokenObj.getExpiresAt() == null || tokenObj.getExpiresAt().before(new Date())) {
            return null;
        }

        return tokenObj;
    }

    @Override
    public void removeCookie(HttpServletResponse response, String cookieName, String path, String domain) {
        Cookie cookie = new Cookie(cookieName, null);
        cookie.setPath(path);
        cookie.setMaxAge(0);
        if (StrUtil.isNotEmpty(domain)) {
            cookie.setDomain(domain);
        }
        response.addCookie(cookie);
    }

    private static final int TTL_DAY = 10;

    @Override
    public RefreshToken createRefreshTokenObj(Long userId, String jti) {
        Date now = new Date();

        // 设置过期时间，比如 10 天后
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.DAY_OF_MONTH, TTL_DAY); // 有效期 10 天
        Date expiresAt = calendar.getTime();

        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setJti(jti);
        token.setIssuedAt(now);
        token.setExpiresAt(expiresAt);
        return token;
    }

    @Override
    public String generateRefreshToken() {
        return IdUtil.simpleUUID();
    }

    @Override
    public void storeRefreshTokenToRedis(String refreshToken, RefreshToken refreshTokenObj) {

        ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
        String refreshTokenJson = "";
        try {
            refreshTokenJson = new ObjectMapper().writeValueAsString(refreshTokenObj);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Refresh Token 序列化失败");
        }
        operations.set(RedisKeyConstant.REFRESH_TOKEN_KEY_PREFIX + refreshToken, refreshTokenJson, TTL_DAY, TimeUnit.DAYS);
    }

    @Override
    public void writeTokenToCookies(HttpServletResponse response, String token, String configKey) {
        CookiesProperties.CookieInfo refreshTokenInfo = cookiesProperties.getCookieConfigs().get(configKey);
        Cookie cookie = CookieUtil.buildCookie(refreshTokenInfo, token);
        String setCookieHeader = CookieUtil.buildSetCookieHeader(cookie, refreshTokenInfo.getSameSite());
        log.error(setCookieHeader);
        response.addHeader("Set-Cookie", setCookieHeader);
    }

    @Override
    public RefreshToken createNewRefreshTokenBasedOnOld(@NotNull RefreshToken old, @NotNull String jti) {
        Long userId = old.getUserId();
        Date issuedAt = old.getIssuedAt();
        Date expiresAt = old.getExpiresAt();
        return new RefreshToken(userId, jti, issuedAt, expiresAt);
    }

    @Override
    @Deprecated
    public boolean removeRefreshTokenFromRedis(String refreshToken) {
        String key = RedisKeyConstant.REFRESH_TOKEN_KEY_PREFIX + refreshToken;
        return Boolean.TRUE.equals(stringRedisTemplate.delete(key));
    }

    @Deprecated
    @Override
    public boolean EqualToJtiFromRefreshToken(String refreshToken, String jti) {
        String key = RedisKeyConstant.REFRESH_TOKEN_KEY_PREFIX + refreshToken;
        ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
        String refreshTokenJson = operations.get(key);
        RefreshToken refreshTokenObj = null;
        if (refreshTokenJson != null)
            refreshTokenObj = JSONUtil.toBean(refreshTokenJson, RefreshToken.class);
        if (refreshTokenObj == null) return false;
        return refreshTokenObj.getJti().equals(jti);
    }

    @Deprecated
    @Override
    public void storeJWTToRedis(Long userId, String jti) {
        ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
        String JWTKey = RedisKeyConstant.JWT_KEY_PREFIX + userId;
        operations.set(JWTKey, jti, JWTRedisTTL, TimeUnit.SECONDS);
    }

    @Override
    public void buildReverseIndex(Long id, String refreshToken) {
        ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
        String key = RedisKeyConstant.REVERSE_INDEX_PREFIX + id;
        operations.set(key, refreshToken, TTL_DAY, TimeUnit.DAYS);
    }

    /**
     * 删除 Redis 中的 refreshToken 和反向索引（原子性、一致性保障）
     */
    @Override
    public boolean removeRefreshTokenAndReverseIndex(String refreshToken, Long userId) {
        String refreshKey = RedisKeyConstant.REFRESH_TOKEN_KEY_PREFIX + refreshToken;
        String reverseKey = RedisKeyConstant.REVERSE_INDEX_PREFIX + userId;
        List<String> keys = Arrays.asList(refreshKey, reverseKey);
        Long result = stringRedisTemplate.execute(deleteByRefreshTokenScript, keys);
        return result != null && result == 1L;
    }

    @Override
    public void removeRefreshTokenAndReverseIndex(Long userId) {
        // 构造 Redis key
        String reverseKey = RedisKeyConstant.REVERSE_INDEX_PREFIX + userId;  // user_refresh_token:{userId}
        String refreshTokenPrefix = RedisKeyConstant.REFRESH_TOKEN_KEY_PREFIX; // refresh_token:
        // 执行脚本（传递 KEYS[1] 和 KEYS[2]）
        List<String> keys = Arrays.asList(reverseKey, refreshTokenPrefix);
        stringRedisTemplate.execute(deleteByUserIdScript, keys);
    }

    @Override
    public void addIntoBlackList(String jti, long userId) {
        String key = RedisKeyConstant.JWT_BLACKLIST_PREFIX + userId;
        ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
        operations.set(key, jti, JWTRedisTTL, TimeUnit.SECONDS);
    }

    @Override
    public boolean notInJWTBlacklist(String jti, Long userId) {
        String key = RedisKeyConstant.JWT_BLACKLIST_PREFIX + userId;
        ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
        String s = operations.get(key);
        return s == null || !s.equals(jti);
    }
}
