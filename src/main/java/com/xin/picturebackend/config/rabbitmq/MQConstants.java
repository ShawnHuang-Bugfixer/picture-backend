package com.xin.picturebackend.config.rabbitmq;

/**
 *
 * @author 黄兴鑫
 * @since 2025/6/27 15:51
 */
public interface MQConstants {
    String AUDIT_QUEUE = "audit.queue";
//    String ACTIVITY_QUEUE = "activity.queue";
    String DLX_EXCHANGE = "x.dlx";
    String DLQ_AUDIT = "dlq.audit";
//    String DLQ_ACTIVITY = "dlq.activity";
    String EXCHANGE = "x.message.direct";
    String ROUTING_AUDIT = "routing.audit";
//    String ROUTING_ACTIVITY = "routing.activity";
}

