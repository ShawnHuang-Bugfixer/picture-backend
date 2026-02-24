package com.xin.picturebackend.config.rabbitmq;

/**
 * RabbitMQ ??
 */
public interface MQConstants {
    // ????
    String AUDIT_QUEUE = "audit.queue";
    String DLX_EXCHANGE = "x.dlx";
    String DLQ_AUDIT = "dlq.audit";
    String EXCHANGE = "x.message.direct";
    String ROUTING_AUDIT = "routing.audit";

    // ????
    String AUDIT_CONTENT_EXCHANGE = "x.audit.content.direct";
    String AUDIT_CONTENT_QUEUE = "audit.content.queue";
    String DLX_AUDIT_CONTENT_EXCHANGE = "x.dlx.audit.content";
    String DLQ_AUDIT_CONTENT = "dlq.audit.content";
    String ROUTING_AUDIT_CONTENT = "routing.audit.content";

    // ?????
    String EMAIL_CODE_EXCHANGE = "x.email.code.direct";
    String EMAIL_CODE_QUEUE = "email.code.queue";
    String ROUTING_EMAIL_CODE = "routing.email.code";
    String DLX_EMAIL_CODE_EXCHANGE = "x.dlx.email.code";
    String DLQ_EMAIL_CODE = "dlq.email.code";

    // ?????Java -> Python?
    String SR_TASK_EXCHANGE = "x.sr.task.direct";
    String SR_TASK_ROUTING_KEY = "sr.task";
    String SR_TASK_QUEUE = "sr.task.queue";

    // ?????Python -> Java?
    String SR_RESULT_EXCHANGE = "x.sr.result.direct";
    String SR_RESULT_ROUTING_KEY = "sr.result";
    String SR_RESULT_QUEUE = "sr.result.java.queue";

    // Python ????????Java ??????
    String SR_RETRY_EXCHANGE = "x.sr.retry.direct";
}
