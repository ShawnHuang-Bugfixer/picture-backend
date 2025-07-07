package com.xin.picturebackend.service.statemachine.actions;

import com.xin.picturebackend.model.entity.Picture;
import com.xin.picturebackend.model.entity.ReviewMessage;
import com.xin.picturebackend.model.enums.PictureReviewStatusEnum;
import com.xin.picturebackend.service.PictureService;
import com.xin.picturebackend.service.UserAppealQuotaService;
import com.xin.picturebackend.service.msgpush.eventpublisher.EventPublisher;
import com.xin.picturebackend.service.statemachine.context.ContextKey;
import com.xin.picturebackend.service.statemachine.events.ImageReviewEvent;
import com.xin.picturebackend.service.statemachine.states.ImageReviewState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;

/**
 *
 * @author 黄兴鑫
 * @since 2025/7/4 15:35
 */
@Component
@Slf4j
public class CommonActions {
    @Resource
    private PictureService pictureService;

    @Resource
    private EventPublisher eventPublisher;

    @Resource
    private UserAppealQuotaService userAppealQuotaService;

    public Action<ImageReviewState, ImageReviewEvent> aiPassAction() {
        // 1. 标记图片最终过审 pending review -> ai pass
        return context -> {
            Picture picture = (Picture)context.getExtendedState().getVariables().get(ContextKey.PICTURE_OBJ_KEY);
            if (picture == null) return;
            log.debug("enter ai pass");
            pictureService.markPictureWithStatus(picture.getId(), PictureReviewStatusEnum.FINAL_APPROVED.getValue(), null, "AI pass");
            String content = "您于 " + picture.getCreateTime() + " 发布的图片：" + picture.getName() + "已过审";
            pushMessage(picture.getUserId(), content);
        };
    }

    @Transactional(rollbackFor = Exception.class)
    public Action<ImageReviewState, ImageReviewEvent> aiRejectAction() {
        return context -> {
            // 1. 标记图片最终拒绝 pending review -> ai reject
            Picture picture = (Picture)context.getExtendedState().getVariables().get(ContextKey.PICTURE_OBJ_KEY);
            if (picture == null) return;
            log.debug("enter ai reject");
            pictureService.markPictureWithStatus(picture.getId(), PictureReviewStatusEnum.FINAL_REJECTED.getValue(), null,"AI reject");
            // 2. 警告用户
            pictureService.warnUser(picture.getUserId());
            // 3. 推送未过审（警告信息）
            String content = "您于 " + picture.getCreateTime() + " 发布的图片：" + picture.getName() + "经审核存在敏感内容！您被警告 1 次，超过三次将永久封禁！";
            pushMessage(picture.getUserId(), content);
        };
    }

    public Action<ImageReviewState, ImageReviewEvent> manualPassAction() {
        return context -> {
            // 1.人工复审通过 pending review -> ai suspicious -> manual pass
            Picture picture = (Picture)context.getExtendedState().getVariables().get(ContextKey.PICTURE_OBJ_KEY);
            Long reviewerId = (Long)context.getExtendedState().getVariables().get(ContextKey.REVIEWER_ID_KEY);
            if (picture == null || reviewerId == null) return;
            log.debug("enter manual pass");
            pictureService.markPictureWithStatus(picture.getId(), PictureReviewStatusEnum.FINAL_APPROVED.getValue(), reviewerId, "manual review pass");
            String content = "您于 " + picture.getCreateTime() + " 发布的图片：" + picture.getName() + "已过审！";
            pushMessage(picture.getUserId(), content);
        };
    }

    public Action<ImageReviewState, ImageReviewEvent> manualRejectAction() {
        return context -> {
            // 1. 标记人工复审拒绝 pending review -> ai suspicious -> manual reject
            Picture picture = (Picture)context.getExtendedState().getVariables().get(ContextKey.PICTURE_OBJ_KEY);
            Long reviewerId = (Long)context.getExtendedState().getVariables().get(ContextKey.REVIEWER_ID_KEY);
            if (picture == null || reviewerId == null) return;
            log.debug("enter manual reject");
            pictureService.markPictureWithStatus(picture.getId(), PictureReviewStatusEnum.FINAL_REJECTED.getValue(), reviewerId, "manual review reject");
            pictureService.warnUser(picture.getUserId());
            String content = "您于 " + picture.getCreateTime() + " 发布的图片：" + picture.getName() + "经审核存在敏感内容！您被警告 1 次，超过三次将永久封禁！";
            pushMessage(picture.getUserId(), content);
        };
    }

    public Action<ImageReviewState, ImageReviewEvent> appealPassAction() {
        // 1. 标记人工申诉通过 finalize reject -> appeal pending -> appeal -> appeal pass
        // 2. 申请次数 ++
        return context -> {
            Picture picture = (Picture)context.getExtendedState().getVariables().get(ContextKey.PICTURE_OBJ_KEY);
            Long reviewerId = (Long)context.getExtendedState().getVariables().get(ContextKey.REVIEWER_ID_KEY);
            if (picture == null || reviewerId == null) return;
            log.debug("enter appeal pass");
            pictureService.markPictureWithStatus(picture.getId(), PictureReviewStatusEnum.FINAL_APPROVED.getValue(), reviewerId, "appeal pass");
            userAppealQuotaService.increaseAppeal(picture.getUserId());
            String content = "您于 " + picture.getCreateTime() + " 发布的图片：" + picture.getName() + "申诉通过，抱歉给您带来的困扰。";
            pushMessage(picture.getUserId(), content);
        };
    }

    @Transactional
    public Action<ImageReviewState, ImageReviewEvent> appealRejectAction() {
        return context -> {
            // 1. 标记图片被拒绝 finalize reject -> appeal pending -> appeal -> appeal reject
            Picture picture = (Picture)context.getExtendedState().getVariables().get(ContextKey.PICTURE_OBJ_KEY);
            Long reviewerId = (Long)context.getExtendedState().getVariables().get(ContextKey.REVIEWER_ID_KEY);
            if (picture == null || reviewerId == null) return;
            log.debug("enter appeal reject");
            pictureService.markPictureWithStatus(picture.getId(), PictureReviewStatusEnum.FINAL_REJECTED.getValue(), reviewerId,"appeal reject");
            userAppealQuotaService.decreaseAppeal(picture.getUserId());
            String content = "您于 " + picture.getCreateTime() + " 发布的图片：" + picture.getName() + "申诉未通过！";
            pushMessage(picture.getUserId(), content);
        };
    }

    private void pushMessage(Long userId, String content) {
        ReviewMessage reviewMessage = ReviewMessage.createReviewMessage(userId, content, new Date());
        eventPublisher.publishMessage(reviewMessage);
    }
}

