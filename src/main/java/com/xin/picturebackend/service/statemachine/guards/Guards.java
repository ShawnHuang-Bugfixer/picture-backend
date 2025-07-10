package com.xin.picturebackend.service.statemachine.guards;

import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.model.entity.Picture;
import com.xin.picturebackend.model.enums.PictureReviewStatusEnum;
import com.xin.picturebackend.service.UserAppealQuotaService;
import com.xin.picturebackend.service.statemachine.context.ContextKey;
import com.xin.picturebackend.service.statemachine.events.ImageReviewEvent;
import com.xin.picturebackend.service.statemachine.states.ImageReviewState;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;

@Component
public class Guards {

    @Resource
    private UserAppealQuotaService userAppealQuotaService;

    @Resource
    private TransactionTemplate transactionTemplate;

    public Guard<ImageReviewState, ImageReviewEvent> AppealGuard() {
        return stateContext -> Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            Picture picture = (Picture) stateContext.getExtendedState().getVariables().get(ContextKey.PICTURE_OBJ_KEY);

            // 检查申诉额度
            if (!userAppealQuotaService.canAppeal(picture.getUserId())) {
                return false;
            }

            // 扣减申诉额度（事务保障下）
            userAppealQuotaService.decreaseAppeal(picture.getUserId());

            return true;
        }));
    }
}


