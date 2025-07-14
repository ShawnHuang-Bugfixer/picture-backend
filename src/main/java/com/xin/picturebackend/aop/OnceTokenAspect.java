package com.xin.picturebackend.aop;

import cn.dev33.satoken.stp.StpUtil;
import com.xin.picturebackend.annotation.OnceTokenRequired;
import com.xin.picturebackend.config.CookiesProperties;
import com.xin.picturebackend.constant.RedisKeyConstant;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.service.TokenService;
import com.xin.picturebackend.utils.CookieUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

@Aspect
@Component
@Slf4j
public class OnceTokenAspect {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private HttpServletRequest request;

    @Resource
    private HttpServletResponse response;

    @Resource
    private TokenService tokenService;

    @Resource
    private CookiesProperties cookiesProperties;

    private String onceTokenName;

    private String path;

    private String domain;

    @PostConstruct
    public void init() {
        CookiesProperties.CookieInfo refreshTokenInfo = cookiesProperties.getCookieConfigs().get("onceToken");
        Cookie onceCookie = CookieUtil.buildCookie(refreshTokenInfo, null);
        onceTokenName = onceCookie.getName();
        path = onceCookie.getPath();
        domain = onceCookie.getDomain();
    }

    @Around("@annotation(com.xin.picturebackend.annotation.OnceTokenRequired)")
    public Object validateOnceToken(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 获取注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OnceTokenRequired annotation = method.getAnnotation(OnceTokenRequired.class);
        boolean needLogin = annotation.needLogin();

        // 2. 获取 token（来自 cookie）
        String token = getOnceToken();

        // 3. 获取 Redis key
        String redisKey;
        long userId = -1;

        if (needLogin) {
            // 登录情况下，使用登录用户 ID
            userId = StpUtil.getLoginIdAsLong();
            redisKey = RedisKeyConstant.ONCE_TOKEN_LOGIN_PREFIX + userId;
        } else {
            // 未登录情况下，token 直接作为 key
            redisKey = RedisKeyConstant.ONCE_TOKEN_NOT_LOGIN_PREFIX + token;
        }

        // 4. 校验 token 是否一致
        String storedToken = stringRedisTemplate.opsForValue().get(redisKey);
        if (storedToken == null || !storedToken.equals(token)) {
            // 记录当前请求 IP 和 token，利于安全审计
            log.warn("非法或重复 token 尝试, IP={}, token={}", request.getRemoteAddr(), token);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Token 无效或已失效");
        }

        // 5. 删除 Redis token
        stringRedisTemplate.delete(redisKey);
        log.debug("删除 Redis 中 token, key {}", redisKey);

        // 6. 删除 Cookie
        tokenService.removeCookie(response, onceTokenName, path, domain);
        log.debug("删除 cookie {}", onceTokenName);

        // 7. 放行请求
        return joinPoint.proceed();
    }

    private String getOnceToken() {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未携带任何 cookie");
        }

        String token = null;
        for (Cookie cookie : cookies) {
            if (onceTokenName.equals(cookie.getName())) {
                token = cookie.getValue();
                break;
            }
        }

        if (token == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未携带 onceToken");
        }
        return token;
    }
}
