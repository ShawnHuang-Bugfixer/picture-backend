package com.xin.picturebackend.aop;

import com.xin.picturebackend.annotation.AuthCheck;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.enums.UserRoleEnum;
import com.xin.picturebackend.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 权限校验切面
 *
 * @author 黄兴鑫
 * @since 2025/2/26 14:46
 */
@Deprecated
@Aspect
@Component
public class AuthInterceptor {
    @Resource
    private UserService userService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        // 1. 获取当前登录用户的权限枚举对象
        // 2. 比较登录用户权限和所需权限决定是否放行
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        User loginUser = userService.getLoginUser(request);
        // 用户未登录
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        UserRoleEnum mustRole = UserRoleEnum.getEnumByValue(authCheck.mustRole());
        // 无需任何权限
        if (mustRole == null) return joinPoint.proceed();
        UserRoleEnum userRole = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        ThrowUtils.throwIf(userRole == null, ErrorCode.NO_AUTH_ERROR);
        // 需要管理员权限
        ThrowUtils.throwIf(mustRole.equals(UserRoleEnum.ADMIN) && !userRole.equals(UserRoleEnum.ADMIN), ErrorCode.NO_AUTH_ERROR);
        // 权限校验通过
        return joinPoint.proceed();
    }
}
