package com.xin.picturebackend.model.messagequeue.sr;

import lombok.Data;

import java.math.BigDecimal;

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
    private Long outputSize;
    private Integer outputWidth;
    private Integer outputHeight;
    private Long durationMs;
    private BigDecimal fps;
    private Integer bitrateKbps;
    private String codec;
    private String extraJson;
}
