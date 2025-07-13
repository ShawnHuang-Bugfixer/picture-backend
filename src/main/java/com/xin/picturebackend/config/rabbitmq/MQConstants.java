package com.xin.picturebackend.config.rabbitmq;

/**
 * @author 黄兴鑫
 * @since 2025/6/27 15:51
 */
public interface MQConstants {
    // 审核消息
    String AUDIT_QUEUE = "audit.queue";
    String DLX_EXCHANGE = "x.dlx";
    String DLQ_AUDIT = "dlq.audit";
    String EXCHANGE = "x.message.direct";
    String ROUTING_AUDIT = "routing.audit";

    // 审核内容
    String AUDIT_CONTENT_EXCHANGE = "x.audit.content.direct";
    String AUDIT_CONTENT_QUEUE = "audit.content.queue";
    String DLX_AUDIT_CONTENT_EXCHANGE = "x.dlx.audit.content";
    String DLQ_AUDIT_CONTENT = "dlq.audit.content";
    String ROUTING_AUDIT_CONTENT = "routing.audit.content";

    // 邮箱验证码交换机与队列
    String EMAIL_CODE_EXCHANGE = "x.email.code.direct";
    String EMAIL_CODE_QUEUE = "email.code.queue";
    String ROUTING_EMAIL_CODE = "routing.email.code";

    // 邮箱验证码死信交换机与队列
    String DLX_EMAIL_CODE_EXCHANGE = "x.dlx.email.code";
    String DLQ_EMAIL_CODE = "dlq.email.code";
}

