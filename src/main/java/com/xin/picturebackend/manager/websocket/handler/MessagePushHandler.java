package com.xin.picturebackend.manager.websocket.handler;

import cn.hutool.json.JSONUtil;
import com.xin.picturebackend.service.msgpush.connections.UserConnectionManager;
import com.xin.picturebackend.service.msgpush.connections.ConnectionType;
import com.xin.picturebackend.service.msgpush.model.MessageInfo;
import com.xin.picturebackend.service.ReviewMessageService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Nonnull;
import javax.annotation.Resource;

/**
 * @author 黄兴鑫
 * @since 2025/6/26 15:35
 */
@Component
public class MessagePushHandler extends TextWebSocketHandler {

    @Resource
    private ReviewMessageService reviewMessageService;

    @Resource
    private UserConnectionManager connectionManager;

    @Override
    public void afterConnectionEstablished(@Nonnull WebSocketSession session) throws Exception {
        Long userId = getUserIdFromSession(session);
        connectionManager.register(userId, ConnectionType.WEBSOCKET, session);
        Long unreadMessageNum = reviewMessageService.getUnreadMessageNum(userId);
        if (unreadMessageNum > 0) {
            MessageInfo messageInfo = new MessageInfo();
            messageInfo.setInfo(unreadMessageNum.toString());
            String responseJson = JSONUtil.toJsonStr(messageInfo);
            session.sendMessage(new TextMessage(responseJson));
        }
    }

    @Override
    public void afterConnectionClosed(@Nonnull WebSocketSession session, @Nonnull CloseStatus status) throws Exception {
        Long userId = getUserIdFromSession(session);
        connectionManager.unregister(userId);
    }

    @Override
    public void handleTextMessage(@Nonnull WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        MessageInfo me = JSONUtil.toBean(payload, MessageInfo.class);
        if ("ping".equalsIgnoreCase(me.getType())) {
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
