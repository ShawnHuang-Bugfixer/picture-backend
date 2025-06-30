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
    public Queue activityQueue() {
        return QueueBuilder.durable(MQConstants.ACTIVITY_QUEUE)
                .withArgument("x-dead-letter-exchange", MQConstants.DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MQConstants.DLQ_ACTIVITY)
                .build();
    }

    @Bean
    public Queue dlqAuditQueue() {
        return QueueBuilder.durable(MQConstants.DLQ_AUDIT).build();
    }

    @Bean
    public Queue dlqActivityQueue() {
        return QueueBuilder.durable(MQConstants.DLQ_ACTIVITY).build();
    }

    @Bean
    public Binding auditBinding() {
        return BindingBuilder.bind(auditQueue())
                .to(messageExchange())
                .with(MQConstants.ROUTING_AUDIT);
    }

    @Bean
    public Binding activityBinding() {
        return BindingBuilder.bind(activityQueue())
                .to(messageExchange())
                .with(MQConstants.ROUTING_ACTIVITY);
    }

    @Bean
    public Binding dlqAuditBinding() {
        return BindingBuilder.bind(dlqAuditQueue())
                .to(dlxExchange())
                .with(MQConstants.DLQ_AUDIT);
    }

    @Bean
    public Binding dlqActivityBinding() {
        return BindingBuilder.bind(dlqActivityQueue())
                .to(dlxExchange())
                .with(MQConstants.DLQ_ACTIVITY);
    }
}

