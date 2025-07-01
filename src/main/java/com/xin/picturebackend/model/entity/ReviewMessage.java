package com.xin.picturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

import com.xin.picturebackend.messagepush.model.IMessage;
import com.xin.picturebackend.messagepush.model.MessageType;
import lombok.Data;

/**
 * 审核消息表
 * @TableName review_message
 */
@TableName(value ="review_message")
@Data
public class ReviewMessage implements IMessage {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 接收用户 ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息状态：0=未读，1=已读
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private Date createdAt;

    /**
     * 阅读时间
     */
    @TableField("read_at")
    private Date readAt;

    @Override
    public String getType() {
        return MessageType.REVIEW.getValue();
    }

    @Override
    public Date getTimestamp() {
        return createdAt;
    }
}