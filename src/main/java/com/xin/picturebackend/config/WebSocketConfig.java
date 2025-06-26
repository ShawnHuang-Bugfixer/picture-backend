package com.xin.picturebackend.config;

import com.xin.picturebackend.manager.websocket.handler.PictureEditHandler;
import com.xin.picturebackend.manager.websocket.handler.WebSocketHandler;
import com.xin.picturebackend.manager.websocket.interceptor.CustomHandshakeInterceptor;
import com.xin.picturebackend.manager.websocket.interceptor.WsHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

/**
 *
 * @author 黄兴鑫
 * @since 2025/3/31 19:54
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private PictureEditHandler pictureEditHandler;

    @Resource
    private WsHandshakeInterceptor wsHandshakeInterceptor;

    @Resource
    private CustomHandshakeInterceptor customHandshakeInterceptor;

    @Resource
    private WebSocketHandler webSocketHandler;
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 图片协同编辑
        registry.addHandler(pictureEditHandler, "/ws/picture/edit")
                .addInterceptors(wsHandshakeInterceptor)
                .setAllowedOrigins("*");

        // 消息推送
        registry.addHandler(webSocketHandler, "/ws/connect")
                .addInterceptors(customHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}

