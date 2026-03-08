package com.xin.picturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 超分任务
 */
@TableName(value = "sr_task")
@Data
public class SrTask implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("task_no")
    private String taskNo;

    @TableField("user_id")
    private Long userId;

    @TableField(exist = false)
    private Long pictureId;

    @TableField("space_id")
    private Long spaceId;

    @TableField("biz_type")
    private String bizType;

    @TableField("input_file_key")
    private String inputFileKey;

    @TableField("output_file_key")
    private String outputFileKey;

    private String status;

    private Integer progress;

    private Integer scale;

    @TableField("model_name")
    private String modelName;

    @TableField("model_version")
    private String modelVersion;

    @TableField("retry_count")
    private Integer attempt;

    @TableField("max_retry")
    private Integer maxRetry;

    @TableField(exist = false)
    private Long costMs;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("trace_id")
    private String traceId;

    @TableField("video_options_json")
    private String videoOptionsJson;

    @TableField("cancel_requested")
    private Integer cancelRequested;

    @TableField("created_at")
    private Date createTime;

    @TableField("updated_at")
    private Date updateTime;

    @TableField(exist = false)
    private Integer isDelete;

    @Serial
    private static final long serialVersionUID = 1L;
}
