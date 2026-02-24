package com.xin.picturebackend.service.messageconsumer;

import cn.hutool.json.JSONUtil;
import com.rabbitmq.client.Channel;
import com.xin.picturebackend.config.rabbitmq.MQConstants;
import com.xin.picturebackend.manager.email.MailSenderManager;
import com.xin.picturebackend.model.messagequeue.email.EmailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.mail.AuthenticationFailedException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 异步处理
 *
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
            byte[] body = message.getBody();
            EmailMessage emailMessage = JSONUtil.toBean(new String(body, StandardCharsets.UTF_8), EmailMessage.class);

            String email = emailMessage.getEmail();
            String code = emailMessage.getCode();

            boolean success = mailSenderManager.sendEmailCode(email, code);
            if (success) {
                channel.basicAck(deliveryTag, false);
                log.info("验证码邮件发送成功: {}", email);
            } else {
                handleRetry(message, channel, deliveryTag, email, null);
            }
        } catch (Exception e) {
            log.error("处理验证码消息异常，消息体：{}，错误：{}", new String(message.getBody(), StandardCharsets.UTF_8), e.getMessage(), e);
            handleRetry(message, channel, deliveryTag, null, e);
        }
    }

    private int getRetryCount(Message message) {
        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        Object count = headers.get("x-retry-count");
        if (count instanceof Integer) {
            return (Integer) count;
        }
        if (count instanceof Long) {
            return ((Long) count).intValue();
        }
        return 0;
    }

    private void handleRetry(Message message, Channel channel, long deliveryTag, String email, Exception exception) {
        int retryCount = getRetryCount(message);
        boolean authError = isMailAuthError(exception);

        try {
            if (authError || retryCount >= MAX_RETRY_COUNT) {
                log.error("验证码邮件发送失败，不再重试，进入死信队列，email={}, retryCount={}, authError={}",
                        email, retryCount, authError);
                channel.basicReject(deliveryTag, false);
                return;
            }

            message.getMessageProperties().getHeaders().put("x-retry-count", retryCount + 1);
            log.warn("验证码邮件发送失败，第 {} 次重试，email={}", retryCount + 1, email);
            channel.basicNack(deliveryTag, false, true);
        } catch (IOException ioException) {
            log.error("RabbitMQ nack/reject 异常: {}", ioException.getMessage(), ioException);
        }
    }

    private boolean isMailAuthError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof MailAuthenticationException || current instanceof AuthenticationFailedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
