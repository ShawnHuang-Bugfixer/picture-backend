package com.xin.picturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 
 * @TableName user_email
 */
@TableName(value ="user_email")
@Data
public class UserEmail {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID，唯一 = 一个用户只能绑定一个邮箱
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 邮箱地址，唯一 = 一个邮箱只能对应一个用户
     */
    private String email;

    /**
     * 是否已验证
     */
    @TableField("is_verified")
    private Integer isVerified;

    /**
     * 状态: 0-正常, 1-冻结, 2-屏蔽
     */
    private Integer status;

    /**
     * 
     */
    @TableField("created_at")
    private Date createdAt;

    /**
     * 
     */
    @TableField("updated_at")
    private Date updatedAt;
}