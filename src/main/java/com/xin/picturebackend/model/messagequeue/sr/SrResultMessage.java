package com.xin.picturebackend.model.messagequeue.sr;

import lombok.Data;

/**
 * Python -> Java 超分结果消息
 */
@Data
public class SrResultMessage {
    private String schemaVersion;
    private String eventId;
    private String timestamp;
    private Long taskId;
    private String status;
    private Integer progress;
    private String outputFileKey;
    private Long costMs;
    private Integer attempt;
    private String errorCode;
    private String errorMsg;
    private String traceId;
}

