package com.xin.picturebackend.config.rabbitmq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author 黄兴鑫
 * @since 2025/6/27 16:07
 */
@Configuration
public class RabbitMQConfig {
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter(); //使用 JSON 格式消息转换器
    }

    @Bean
    public DirectExchange messageExchange() {
        return new DirectExchange(MQConstants.EXCHANGE);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(MQConstants.DLX_EXCHANGE);
    }

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(MQConstants.AUDIT_QUEUE)
                .withArgument("x-dead-letter-exchange", MQConstants.DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MQConstants.DLQ_AUDIT)
                .build();
    }

    @Bean
    public Queue dlqAuditQueue() {
        return QueueBuilder.durable(MQConstants.DLQ_AUDIT).build();
    }


    @Bean
    public Binding auditBinding() {
        return BindingBuilder.bind(auditQueue())
                .to(messageExchange())
                .with(MQConstants.ROUTING_AUDIT);
    }


    @Bean
    public Binding dlqAuditBinding() {
        return BindingBuilder.bind(dlqAuditQueue())
                .to(dlxExchange())
                .with(MQConstants.DLQ_AUDIT);
    }

    // 审核内容交换机
    @Bean
    public DirectExchange auditContentExchange() {
        return new DirectExchange(MQConstants.AUDIT_CONTENT_EXCHANGE);
    }

    // 审核内容死信交换机
    @Bean
    public DirectExchange dlxAuditContentExchange() {
        return new DirectExchange(MQConstants.DLX_AUDIT_CONTENT_EXCHANGE);
    }

    // 审核内容主队列
    @Bean
    public Queue auditContentQueue() {
        return QueueBuilder.durable(MQConstants.AUDIT_CONTENT_QUEUE)
                .withArgument("x-dead-letter-exchange", MQConstants.DLX_AUDIT_CONTENT_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MQConstants.DLQ_AUDIT_CONTENT)
                .build();
    }

    // 审核内容死信队列
    @Bean
    public Queue dlqAuditContentQueue() {
        return QueueBuilder.durable(MQConstants.DLQ_AUDIT_CONTENT).build();
    }

    // 主队列绑定
    @Bean
    public Binding auditContentBinding() {
        return BindingBuilder.bind(auditContentQueue())
                .to(auditContentExchange())
                .with(MQConstants.ROUTING_AUDIT_CONTENT);
    }

    // 死信队列绑定
    @Bean
    public Binding dlqAuditContentBinding() {
        return BindingBuilder.bind(dlqAuditContentQueue())
                .to(dlxAuditContentExchange())
                .with(MQConstants.DLQ_AUDIT_CONTENT);
    }
}

