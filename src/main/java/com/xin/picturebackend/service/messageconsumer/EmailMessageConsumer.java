package com.xin.picturebackend.service.messageconsumer;

import cn.hutool.json.JSONUtil;
import com.rabbitmq.client.Channel;
import com.xin.picturebackend.config.rabbitmq.MQConstants;
import com.xin.picturebackend.manager.email.MailSenderManager;
import com.xin.picturebackend.model.messagequeue.email.EmailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 异步处理
 *
 * @author 黄兴鑫
 * @since 2025/7/13 9:19
 */
@Service
@Slf4j
public class EmailMessageConsumer {
    @Resource
    private MailSenderManager mailSenderManager;

    private static final int MAX_RETRY_COUNT = 3;

    @RabbitListener(queues = MQConstants.EMAIL_CODE_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void receiveEmailCodeMessage(Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            // 反序列化消息体
            byte[] body = message.getBody();
            EmailMessage emailMessage = JSONUtil.toBean(new String(body, StandardCharsets.UTF_8), EmailMessage.class);

            String email = emailMessage.getEmail();
            String code = emailMessage.getCode();

            // 执行邮件发送
            boolean success = mailSenderManager.sendEmailCode(email, code);

            if (success) {
                channel.basicAck(deliveryTag, false); // 正常确认
                log.info("验证码邮件发送成功: {}", email);
            } else {
                int retryCount = getRetryCount(message);
                if (retryCount >= MAX_RETRY_COUNT) {
                    log.error("验证码邮件重试超过上限，将进入死信队列: {}", email);
                    channel.basicReject(deliveryTag, false); // 不重回队列，进入 DLQ
                } else {
                    log.warn("验证码邮件发送失败，第 {} 次尝试，将重试: {}", retryCount + 1, email);
                    channel.basicNack(deliveryTag, false, true); // 重入队列
                }
            }

        } catch (Exception e) {
            log.error("处理验证码消息异常，消息体：{}，错误：{}", new String(message.getBody()), e.getMessage(), e);
            try {
                channel.basicNack(deliveryTag, false, true); // 出现异常也重试
            } catch (IOException ioException) {
                log.error("RabbitMQ nack 异常: {}", ioException.getMessage(), ioException);
            }
        }
    }

    /**
     * 提取消息历史重试次数（基于 RabbitMQ 死信机制）
     */
    private int getRetryCount(Message message) {
        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        List<Map<String, Object>> xDeath = (List<Map<String, Object>>) headers.get("x-death");
        if (xDeath != null && !xDeath.isEmpty()) {
            Object count = xDeath.get(0).get("count");
            if (count instanceof Long) {
                return ((Long) count).intValue();
            }
        }
        return 0;
    }

}
