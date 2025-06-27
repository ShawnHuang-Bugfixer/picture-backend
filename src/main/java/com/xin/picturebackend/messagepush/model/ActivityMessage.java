package com.xin.picturebackend.messagepush.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActivityMessage implements IMessage {

    private Long receiverId;             // 接收用户 ID
    private String activityTitle;        // 活动标题
    private String activityDescription;  // 活动内容
    private LocalDateTime timestamp;     // 活动发布时间

    @Override
    public Long getReceiverId() {
        return receiverId;
    }

    @Override
    public String getType() {
        return "activity";
    }

    @Override
    public String getContent() {
        // 这里可以自定义格式，比如 JSON 序列化
        return String.format("【%s】%s", activityTitle, activityDescription);
    }

    @Override
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}

