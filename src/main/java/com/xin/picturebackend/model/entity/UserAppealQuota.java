package com.xin.picturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 用户申诉配额表
 * @TableName user_appeal_quota
 */
@TableName(value ="user_appeal_quota")
@Data
public class UserAppealQuota {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 当前记录的起始周日期(如周一)
     */
    @TableField("week_start_date")
    private Date weekStartDate;

    /**
     * 已使用的申诉次数(最大为2)
     */
    @TableField("appeal_used")
    private Integer appealUsed;

    /**
     * 更新时间
     */
    @TableField("updated_time")
    private Date updatedTime;

    /**
     * 创建时间
     */
    @TableField("created_time")
    private Date createdTime;
}