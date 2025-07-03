package com.xin.picturebackend.config;

import com.xin.picturebackend.manager.websocket.handler.PictureEditHandler;
import com.xin.picturebackend.manager.websocket.handler.MessagePushHandler;
import com.xin.picturebackend.manager.websocket.interceptor.MessageHandshakeInterceptor;
import com.xin.picturebackend.manager.websocket.interceptor.PictureHandshakeInterceptor;
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
    private PictureHandshakeInterceptor pictureHandshakeInterceptor;

    @Resource
    private MessageHandshakeInterceptor customHandshakeInterceptor;

    @Resource
    private MessagePushHandler messagePushHandler;
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 图片协同编辑
        registry.addHandler(pictureEditHandler, "/ws/picture/edit")
                .addInterceptors(pictureHandshakeInterceptor)
                .setAllowedOrigins("*");

        // 消息推送
        registry.addHandler(messagePushHandler, "/ws/messagepush/connect")
                .addInterceptors(customHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}

