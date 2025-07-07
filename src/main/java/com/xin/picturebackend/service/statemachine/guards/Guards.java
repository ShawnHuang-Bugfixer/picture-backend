package com.xin.picturebackend.service.statemachine.guards;

import com.xin.picturebackend.model.entity.Picture;
import com.xin.picturebackend.service.UserAppealQuotaService;
import com.xin.picturebackend.service.statemachine.context.ContextKey;
import com.xin.picturebackend.service.statemachine.events.ImageReviewEvent;
import com.xin.picturebackend.service.statemachine.states.ImageReviewState;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class Guards {
    @Resource
    private UserAppealQuotaService userAppealQuotaService;

    public Guard<ImageReviewState, ImageReviewEvent> AppealGuard() {
        return stateContext -> {
            Picture picture = (Picture)stateContext.getExtendedState().getVariables().get(ContextKey.PICTURE_OBJ_KEY);
            return userAppealQuotaService.canAppeal(picture.getUserId());
        };
    }
}

