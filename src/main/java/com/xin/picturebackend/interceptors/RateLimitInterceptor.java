package com.xin.picturebackend.interceptors;

import com.xin.picturebackend.service.RateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

@Component
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    @Resource
    private RateLimiterService rateLimiterService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String ip = getIp(request);
        // 获取完整的方法签名（包含包名、类名和方法名）
        Method method = handlerMethod.getMethod();
        String methodName = method.getDeclaringClass().getName() + "." + method.getName();
        String key = ip + ":" + methodName;
        log.debug("key is {}", key);

        boolean acquired = rateLimiterService.getRateLimiter(key).tryAcquire();

        if (!acquired) {
            response.setStatus(429); // Too Many Requests
            response.getWriter().write("请求频率过高，请稍后再试。");
            log.debug("too many requests!");
            return false;
        }

        return true;
    }

    private String getIp(HttpServletRequest request) {
        // 直接获取远程地址作为默认值
        String ip = request.getRemoteAddr();

        // 虽然不用Nginx，但仍检查常见的代理头部以防万一
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            ip = xForwardedFor.split(",")[0].trim();
        }

        return ip;
    }
}
