package com.xin.picturebackend.config.rabbitmq;

/**
 * @author 黄兴鑫
 * @since 2025/6/27 15:51
 */
public interface MQConstants {
    String AUDIT_QUEUE = "audit.queue";
    String DLX_EXCHANGE = "x.dlx";
    String DLQ_AUDIT = "dlq.audit";
    String EXCHANGE = "x.message.direct";
    String ROUTING_AUDIT = "routing.audit";
    String AUDIT_CONTENT_EXCHANGE = "x.audit.content.direct";
    String AUDIT_CONTENT_QUEUE = "audit.content.queue";
    String DLX_AUDIT_CONTENT_EXCHANGE = "x.dlx.audit.content";
    String DLQ_AUDIT_CONTENT = "dlq.audit.content";
    String ROUTING_AUDIT_CONTENT = "routing.audit.content";
}

