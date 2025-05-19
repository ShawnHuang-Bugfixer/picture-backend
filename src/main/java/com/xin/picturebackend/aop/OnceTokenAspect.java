package com.xin.picturebackend.aop;

import cn.dev33.satoken.stp.StpUtil;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
        // 1. 获取 token
        Cookie[] cookies = request.getCookies();
        if (cookies == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "未携带任何 cookie");

        String token = null;
        for (Cookie cookie : cookies) {
            if (onceTokenName.equals(cookie.getName())) {
                token = cookie.getValue();
                break;
            }
        }
        if (token == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "未携带任何 onceToken");

        // 2. 获取当前用户 ID
        long userId = StpUtil.getLoginIdAsLong();
        String redisKey = RedisKeyConstant.ONCE_TOKEN_PREFIX + userId;

        // 3. 校验 token 是否匹配
        String storedToken = stringRedisTemplate.opsForValue().get(redisKey);
        if (!token.equals(storedToken)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR ,"Token invalid or already used");
        }

        // 4. 删除 Redis 中 token
        stringRedisTemplate.delete(redisKey);
        log.debug("删除 Redis 中 token, key {}", redisKey);

        // 5. 删除 cookie
        tokenService.removeCookie(response, onceTokenName, path, domain);
        log.debug("删除 cookie {}", onceTokenName);

        // 6. 放行
        return joinPoint.proceed();
    }
}
