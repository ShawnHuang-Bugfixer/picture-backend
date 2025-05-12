package com.xin.picturebackend.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xin.picturebackend.common.BaseResponse;
import com.xin.picturebackend.common.ResultUtils;
import com.xin.picturebackend.exception.ErrorCode;
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

        boolean acquired = rateLimiterService.getRateLimiter(key).tryAcquire();

        if (!acquired) {
            BaseResponse<?> error = ResultUtils.error(ErrorCode.TOO_MANY_REQUEST, "请求频率过高，请稍后再试。");

            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            ObjectMapper objectMapper = new ObjectMapper();
            String jsonResponse = objectMapper.writeValueAsString(error);
            response.getWriter().write(jsonResponse);

            log.debug("key :{} 请求频率过高！", key);
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
