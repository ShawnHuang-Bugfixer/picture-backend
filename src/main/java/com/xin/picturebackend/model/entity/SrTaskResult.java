package com.xin.picturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 超分结果
 */
@TableName(value = "sr_task_result")
@Data
public class SrTaskResult implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("task_no")
    private String taskNo;

    @TableField("user_id")
    private Long userId;

    @TableField("space_id")
    private Long spaceId;

    @TableField("biz_type")
    private String bizType;

    @TableField("model_name")
    private String modelName;

    @TableField("model_version")
    private String modelVersion;

    @TableField("output_file_key")
    private String outputFileKey;

    @TableField("output_format")
    private String outputFormat;

    @TableField("output_size")
    private Long outputSize;

    @TableField("output_width")
    private Integer outputWidth;

    @TableField("output_height")
    private Integer outputHeight;

    @TableField("duration_ms")
    private Long durationMs;

    private BigDecimal fps;

    @TableField("bitrate_kbps")
    private Integer bitrateKbps;

    private String codec;

    @TableField("cost_ms")
    private Long costMs;

    private Integer attempt;

    @TableField("trace_id")
    private String traceId;

    @TableField("extra_json")
    private String extraJson;

    @TableField("created_at")
    private Date createTime;

    @TableField("updated_at")
    private Date updateTime;

    @TableLogic
    @TableField("is_delete")
    private Integer isDelete;

    @Serial
    private static final long serialVersionUID = 1L;
}
