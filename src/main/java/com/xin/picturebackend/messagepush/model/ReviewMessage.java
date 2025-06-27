package com.xin.picturebackend.messagepush.model;

import java.time.LocalDateTime;

/**
 *
 * @author 黄兴鑫
 * @since 2025/6/26 17:44
 */
public class ReviewMessage implements IMessage {
    private Long receiverId;
    private String content;
    private LocalDateTime timestamp;  //审核时间

    public ReviewMessage(Long receiverId, String content) {
        this.receiverId = receiverId;
        this.content = content;
    }

    @Override
    public Long getReceiverId() {
        return receiverId;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public String getType() {
        return "REVIEW";
    }

    @Override
    public LocalDateTime getTimestamp() {
        return null;
    }
}
