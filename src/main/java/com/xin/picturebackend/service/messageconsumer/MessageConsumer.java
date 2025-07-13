package com.xin.picturebackend.service.messageconsumer;

import cn.hutool.json.JSONUtil;
import com.rabbitmq.client.Channel;
import com.xin.picturebackend.config.rabbitmq.MQConstants;
import com.xin.picturebackend.service.msgpush.connections.UserConnectionManager;
import com.xin.picturebackend.service.msgpush.connections.ConnectionType;
import com.xin.picturebackend.model.entity.ReviewMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 消息队列消费者，异步消费消息队列消息，并根据不同的消息类型调用不同的消息处理器。
 *
 * @author 黄兴鑫
 * @since 2025/6/30 11:33
 */
@Service
@Slf4j
public class MessageConsumer {

    @Resource
    private UserConnectionManager connectionManager;

    @RabbitListener(queues = MQConstants.AUDIT_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void receiveAuditMessage(Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            // 反序列化为 ReviewMessage
            byte[] body = message.getBody();
            ReviewMessage reviewMessage = JSONUtil.toBean(new String(body, StandardCharsets.UTF_8), ReviewMessage.class);

            Long userId = reviewMessage.getUserId();
            if (!connectionManager.isOnline(userId)) {
                log.info("用户 {} 不在线，跳过推送", userId);
                channel.basicAck(deliveryTag, false); // 正常消费但无需推送
                return;
            }

            ConnectionType connectionType = connectionManager.getConnectionType(userId);
            Object connectionRef = connectionManager.getConnectionRef(userId);

            // 推送消息
            if (connectionType == ConnectionType.WEBSOCKET && connectionRef instanceof WebSocketSession session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(JSONUtil.toJsonStr(reviewMessage)));
                }
            } else if (connectionType == ConnectionType.SSE && connectionRef instanceof SseEmitter emitter) {
                emitter.send(reviewMessage, MediaType.APPLICATION_JSON);
            }

            // 手动确认消息
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("处理审核消息失败，消息体：{}，错误：{}", new String(message.getBody()), e.getMessage(), e);
            try {
                // 消息处理失败，拒绝并不重入队列
                channel.basicReject(deliveryTag, false);
            } catch (IOException ioException) {
                log.error("RabbitMQ 手动 reject 消息失败: {}", ioException.getMessage(), ioException);
            }
        }
    }

}
