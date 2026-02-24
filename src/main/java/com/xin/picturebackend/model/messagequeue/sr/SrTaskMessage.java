package com.xin.picturebackend.model.messagequeue.sr;

import lombok.Data;

/**
 * Java -> Python 超分任务消息
 */
@Data
public class SrTaskMessage {
    private String schemaVersion;
    private String eventId;
    private String timestamp;
    private Long taskId;
    private String taskNo;
    private Long userId;
    private String type;
    private String inputFileKey;
    private Integer scale;
    private String modelName;
    private String modelVersion;
    private Integer attempt;
    private String traceId;
}

