package com.xin.picturebackend.model.messagequeue.messagepush;

import java.util.Date;

/**
 *
 * @author 黄兴鑫
 * @since 2025/6/26 17:44
 */
public interface IMessage {
    String getContent();  // 消息内容
    String getType();     // 消息类型标识
    Date getTimestamp(); // 消息发布时间
}

