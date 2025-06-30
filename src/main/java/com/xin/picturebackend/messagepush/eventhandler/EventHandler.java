package com.xin.picturebackend.messagepush.eventhandler;

import com.xin.picturebackend.config.rabbitmq.MQConstants;
import com.xin.picturebackend.messagepush.model.IMessage;
import com.xin.picturebackend.messagepush.model.MessageEvent;
import com.xin.picturebackend.messagepush.model.MessageType;
import com.xin.picturebackend.model.entity.ReviewMessage;
import com.xin.picturebackend.service.ReviewMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;

/**
 * @author 黄兴鑫
 * @since 2025/6/27 13:54
 */
@Slf4j
@Component
public class EventHandler {
    @Resource
    private AmqpTemplate amqpTemplate;

    @Resource
    private ReviewMessageService reviewMessageService;

    @Async("messageEventExecutor")
    @EventListener
    public void onMessageEvent(MessageEvent event) {
        handleMessage(event);
    }

    private void handleMessage(MessageEvent event) {
        IMessage message = event.getMessage();
        if (message.getType().equals(MessageType.REVIEW.getValue())) {
            ReviewMessage reviewMessage = (ReviewMessage) message;
            reviewMessageService.save(reviewMessage);
            amqpTemplate.convertAndSend(MQConstants.EXCHANGE, MQConstants.ROUTING_AUDIT, message);
        }
    }
}
