package com.xin.picturebackend.service.msgpush.eventpublisher;

import com.xin.picturebackend.config.rabbitmq.MQConstants;
import com.xin.picturebackend.model.messagequeue.messagepush.IMessage;
import com.xin.picturebackend.model.messagequeue.messagepush.MessageEvent;
import com.xin.picturebackend.model.messagequeue.messagepush.MessageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.RejectedExecutionException;

/**
 *
 * @author 黄兴鑫
 * @since 2025/6/27 13:48
 */
@Component
@Slf4j
public class EventPublisher {
    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    // 最大重试次数
    @Value("${app.retry.event.max}")
    private int maxTimes;

    @Resource
    private AmqpTemplate amqpTemplate;
    public void publishMessage(IMessage message) {
        MessageEvent messageEvent = new MessageEvent(this, message);
        int retryCount = 0;
        while (retryCount <= maxTimes) {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                applicationEventPublisher.publishEvent(messageEvent);
                break; // 成功发布，退出重试循环
            } catch (RejectedExecutionException e) {
                retryCount++;
                if (retryCount > maxTimes) {
                    // 超过最大重试次数，进入死信队列。
                    String routingKey = MQConstants.DLQ_AUDIT;
                    if (message.getType().equals(MessageType.REVIEW.getValue())) routingKey = MQConstants.DLQ_AUDIT;
                    amqpTemplate.convertAndSend(MQConstants.DLX_EXCHANGE, routingKey, message);
                    log.warn("发布事件重试次数超过{} 次！异常信息：{}, 当前线程 {}", maxTimes, e, Thread.currentThread().getName());
                    break;
                }
                // 指数退避等待
                try {
                    Thread.sleep(200L * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}

