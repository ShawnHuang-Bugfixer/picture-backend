package com.xin.picturebackend.manager.websocket.handler;

import com.xin.picturebackend.manager.UserConnectionManager;
import com.xin.picturebackend.manager.connections.ConnectionType;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Nonnull;
import javax.annotation.Resource;

/**
 *
 *
 * @author 黄兴鑫
 * @since 2025/6/26 15:35
 */
@Component
public class WebSocketHandler extends TextWebSocketHandler {

    @Resource
    private UserConnectionManager connectionManager;

    @Override

    public void afterConnectionEstablished(@Nonnull WebSocketSession session) throws Exception {
        Long userId = getUserIdFromSession(session); // 从 token 或参数中获取
        connectionManager.register(userId, ConnectionType.WEBSOCKET, session);
    }

    @Override
    public void afterConnectionClosed(@Nonnull WebSocketSession session, @Nonnull CloseStatus status) throws Exception {
        Long userId = getUserIdFromSession(session);
        connectionManager.unregister(userId);
    }

    @Override
    public void handleTextMessage(@Nonnull WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if ("ping".equalsIgnoreCase(payload)) {
            Long userId = getUserIdFromSession(session);
            connectionManager.updateHeartbeat(userId);
            // 回复 pong 给前端，确认连接活跃
            session.sendMessage(new TextMessage("pong"));
        }
    }
    private Long getUserIdFromSession(WebSocketSession session) {
        // 从 URL、Header、Session中获取 userId
        String idStr = session.getAttributes().get("userId").toString();
        return Long.valueOf(idStr);
    }
}
