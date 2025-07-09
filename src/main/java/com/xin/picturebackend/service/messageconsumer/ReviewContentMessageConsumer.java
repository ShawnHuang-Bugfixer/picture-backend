package com.xin.picturebackend.service.messageconsumer;

import cn.hutool.json.JSONUtil;
import com.rabbitmq.client.Channel;
import com.xin.picturebackend.config.rabbitmq.MQConstants;
import com.xin.picturebackend.manager.review.ReviewManager;
import com.xin.picturebackend.manager.review.modle.AIReviewResultEnum;
import com.xin.picturebackend.model.entity.Picture;
import com.xin.picturebackend.model.enums.PictureReviewStatusEnum;
import com.xin.picturebackend.model.messagequeue.review.ReviewContentMessage;
import com.xin.picturebackend.service.PictureService;
import com.xin.picturebackend.service.statemachine.context.ContextKey;
import com.xin.picturebackend.service.statemachine.events.ImageReviewEvent;
import com.xin.picturebackend.service.statemachine.states.ImageReviewState;
import com.xin.picturebackend.utils.StateMachineUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 处理消息队列中的图片审核内容
 *
 * @author 黄兴鑫
 * @since 2025/7/8 16:53
 */
@Service
@Slf4j
public class ReviewContentMessageConsumer {
    @Resource
    private PictureService pictureService;

    @Resource
    private ReviewManager reviewManager;

    @Resource
    private StateMachineFactory<ImageReviewState, ImageReviewEvent> stateMachineFactory;

    @RabbitListener(queues = MQConstants.AUDIT_CONTENT_QUEUE)
    public void reviewContentMessageHandler(Message message, Channel channel) {
        // 0. 准备 Pending Review 状态机
        StateMachine<ImageReviewState, ImageReviewEvent> stateMachine = StateMachineUtils.getStateMachine(stateMachineFactory, ImageReviewState.PENDING_REVIEW);
        stateMachine.start();
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // 1. 消息队列获取审核内容
            byte[] body = message.getBody();
            ReviewContentMessage reviewContent = JSONUtil.toBean(new String(body, StandardCharsets.UTF_8), ReviewContentMessage.class);
            Long picId = reviewContent.getPicId();
            Picture picture = pictureService.getById(picId);
            if (picture == null) {
                // 手动确认消息
                channel.basicAck(deliveryTag, false);
                return;
            }
            // 1.1 设置状态机上下文
            StateMachineUtils.setStateMachineExtendedState(stateMachine, ContextKey.PICTURE_OBJ_KEY, picture);
            // 2. 调用第三方接口同步审核
            AIReviewResultEnum aiReviewResultEnum = reviewManager.syncAIReview(picId);
            // 3. 发布事件，触发状态机自动流程切换。
            switch (aiReviewResultEnum) {
                case AI_PASS -> stateMachine.sendEvent(ImageReviewEvent.AI_REVIEW_PASS);
                case AI_REJECT -> stateMachine.sendEvent(ImageReviewEvent.AI_REVIEW_REJECT);
                case AI_SUSPICIOUS -> stateMachine.sendEvent(ImageReviewEvent.AI_REVIEW_SUSPICIOUS);
            }
            // 手动确认消息
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理审核消息失败，消息体：{}，错误：{}", new String(message.getBody()), e.getMessage(), e);
            try {
                // 消息处理失败，拒绝并不重入队列，进入死信队列
                channel.basicReject(deliveryTag, false);
            } catch (IOException ioException) {
                log.error("RabbitMQ 手动 reject 消息失败: {}", ioException.getMessage(), ioException);
            }
        }
    }
}
