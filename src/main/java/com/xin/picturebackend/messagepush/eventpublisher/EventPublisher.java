package com.xin.picturebackend.messagepush.eventpublisher;

import com.xin.picturebackend.messagepush.model.IMessage;
import com.xin.picturebackend.messagepush.model.MessageEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 *
 * @author 黄兴鑫
 * @since 2025/6/27 13:48
 */
@Component
public class EventPublisher {
    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    public void publishMessage(IMessage message) {
        MessageEvent messageEvent = new MessageEvent(this, message);
        applicationEventPublisher.publishEvent(messageEvent);
    }
}
