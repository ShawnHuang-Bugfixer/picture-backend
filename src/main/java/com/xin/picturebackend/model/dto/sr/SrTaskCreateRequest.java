package com.xin.picturebackend.model.dto.sr;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建超分任务请求
 */
@Data
public class SrTaskCreateRequest implements Serializable {

    /**
     * 可选，关联图片 ID
     */
    private Long pictureId;

    /**
     * 输入对象存储 key
     */
    private String inputFileKey;

    /**
     * 超分倍率，建议 2 或 4
     */
    private Integer scale;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 模型版本
     */
    private String modelVersion;

    @Serial
    private static final long serialVersionUID = 1L;
}

