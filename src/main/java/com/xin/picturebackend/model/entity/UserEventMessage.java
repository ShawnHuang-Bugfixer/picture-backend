package com.xin.picturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 用户接收的活动消息关联表
 * @TableName user_event_message
 */
@TableName(value ="user_event_message")
@Data
public class UserEventMessage {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 接收用户 ID
     */
    private Long user_id;

    /**
     * 活动消息模板 ID
     */
    private Long event_message_id;

    /**
     * 消息状态：0=未读，1=已读
     */
    private Integer status;

    /**
     * 阅读时间
     */
    private Date read_at;
}