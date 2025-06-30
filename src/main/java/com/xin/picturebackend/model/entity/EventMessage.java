package com.xin.picturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 活动消息模板表
 * @TableName event_message
 */
@TableName(value ="event_message")
@Data
public class EventMessage {
    /**
     * 活动消息模板 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 消息标题
     */
    private String title;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 创建时间
     */
    private Date created_at;

    /**
     * 过期时间（可选）
     */
    private Date expire_at;
}