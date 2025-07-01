package com.xin.picturebackend.messagepush.messageconsumer;

import cn.hutool.json.JSONUtil;
import com.xin.picturebackend.config.rabbitmq.MQConstants;
import com.xin.picturebackend.manager.UserConnectionManager;
import com.xin.picturebackend.manager.connections.ConnectionType;
import com.xin.picturebackend.model.entity.ReviewMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 消息队列消费者，异步消费消息队列消息，并根据不同的消息类型调用不同的消息处理器。
 *
 * @author 黄兴鑫
 * @since 2025/6/30 11:33
 */
@Component
@Slf4j
public class MessageConsumer {

    @Resource
    private UserConnectionManager connectionManager;

    @RabbitListener(queues = MQConstants.AUDIT_QUEUE)
    public void receiveAuditMessage(ReviewMessage message) {
        Long userId = message.getUserId();
        boolean online = connectionManager.isOnline(userId);
        if (!online) return;
        ConnectionType connectionType = connectionManager.getConnectionType(userId);
        Object connectionRef = connectionManager.getConnectionRef(userId);
        try {
            if (connectionType == ConnectionType.WEBSOCKET && connectionRef instanceof WebSocketSession session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(JSONUtil.toJsonStr(message)));
                }
            } else if (connectionType == ConnectionType.SSE && connectionRef instanceof SseEmitter emitter) {
                emitter.send(message, MediaType.APPLICATION_JSON);
            }
        } catch (IOException e) {
            log.error("推送消息给用户 {} 失败: {}", userId, e.getMessage());
        }
    }
}
