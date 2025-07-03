package com.xin.picturebackend.service.msgpush.model;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 *
 * @author 黄兴鑫
 * @since 2025/6/26 17:49
 */
@Getter
public class MessageEvent extends ApplicationEvent {
    private final IMessage message;

    public MessageEvent(Object source, IMessage message) {
        super(source);
        this.message = message;
    }
}

