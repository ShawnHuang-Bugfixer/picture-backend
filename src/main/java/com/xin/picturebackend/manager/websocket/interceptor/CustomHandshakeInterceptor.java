package com.xin.picturebackend.manager.websocket.interceptor;

import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Nonnull;
import javax.annotation.RegEx;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 *
 * @author 黄兴鑫
 * @since 2025/6/26 15:38
 */
@Slf4j
@Component
public class CustomHandshakeInterceptor implements HandshakeInterceptor {
    @Resource
    private UserService userService;

    @Override
    public boolean beforeHandshake(@Nonnull ServerHttpRequest request,@Nonnull ServerHttpResponse response,
                                   @Nonnull WebSocketHandler wsHandler,
                                   @Nonnull Map<String, Object> attributes) throws Exception {
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
//        User loginUser = userService.getLoginUser(servletRequest);
        attributes.put("userId", 123);
        return true;
    }

    @Override
    public void afterHandshake(@Nonnull ServerHttpRequest request, @Nonnull ServerHttpResponse response,
                               @Nonnull WebSocketHandler wsHandler, Exception ex) {
        log.debug("结束握手");
    }
}

