package com.xin.picturebackend.messagepush.model;

import java.time.LocalDateTime;

/**
 *
 * @author 黄兴鑫
 * @since 2025/6/26 17:44
 */
public interface IMessage {
    Long getReceiverId(); // 接收人
    String getContent();  // 消息内容
    String getType();     // 消息类型标识
    LocalDateTime getTimestamp(); // 消息发布时间
}

