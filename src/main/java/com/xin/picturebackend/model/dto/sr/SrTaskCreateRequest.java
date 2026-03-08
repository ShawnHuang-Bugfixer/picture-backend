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
     * 任务类型：image / video，默认 image
     */
    private String type;

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

    /**
     * 视频任务参数
     */
    private VideoOptions videoOptions;

    @Data
    public static class VideoOptions implements Serializable {
        /**
         * 保留音频
         */
        private Boolean keepAudio;

        /**
         * 先抽帧再超分
         */
        private Boolean extractFrameFirst;

        /**
         * 覆盖输出帧率（可选，> 0）
         */
        private Double fpsOverride;

        @Serial
        private static final long serialVersionUID = 1L;
    }

    @Serial
    private static final long serialVersionUID = 1L;
}
