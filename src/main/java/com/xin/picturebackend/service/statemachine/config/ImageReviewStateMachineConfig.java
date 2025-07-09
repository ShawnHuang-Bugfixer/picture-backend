// config/ImageReviewStateMachineConfig.java
package com.xin.picturebackend.service.statemachine.config;

import com.xin.picturebackend.service.statemachine.actions.CommonActions;
import com.xin.picturebackend.service.statemachine.guards.Guards;
import com.xin.picturebackend.service.statemachine.events.ImageReviewEvent;
import com.xin.picturebackend.service.statemachine.states.ImageReviewState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.*;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import javax.annotation.Resource;
import java.util.EnumSet;

@Configuration
@EnableStateMachineFactory
public class ImageReviewStateMachineConfig extends EnumStateMachineConfigurerAdapter<ImageReviewState, ImageReviewEvent> {

    @Resource
    private CommonActions actions;

    @Resource
    private Guards guards;

    @Override
    public void configure(StateMachineStateConfigurer<ImageReviewState, ImageReviewEvent> states) throws Exception {
        states.withStates()
                .initial(ImageReviewState.PENDING_REVIEW)
                .states(EnumSet.allOf(ImageReviewState.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<ImageReviewState, ImageReviewEvent> transitions) throws Exception {
        transitions
                // AI 审核阶段
                .withExternal().source(ImageReviewState.PENDING_REVIEW).target(ImageReviewState.AI_PASS)
                .event(ImageReviewEvent.AI_REVIEW_PASS).action(actions.aiPassAction()).and()

                .withExternal().source(ImageReviewState.PENDING_REVIEW).target(ImageReviewState.AI_REJECTED)
                .event(ImageReviewEvent.AI_REVIEW_REJECT).action(actions.aiRejectAction()).and()

                .withExternal().source(ImageReviewState.PENDING_REVIEW).target(ImageReviewState.AI_SUSPICIOUS)
                .event(ImageReviewEvent.AI_REVIEW_SUSPICIOUS).action(actions.aiSuspiciousAction()).and()

                // 人工复审阶段
                .withExternal().source(ImageReviewState.AI_SUSPICIOUS).target(ImageReviewState.MANUAL_PASS)
                .event(ImageReviewEvent.MANUAL_REVIEW_PASS).action(actions.manualPassAction()).and()

                .withExternal().source(ImageReviewState.AI_SUSPICIOUS).target(ImageReviewState.MANUAL_REJECTED)
                .event(ImageReviewEvent.MANUAL_REVIEW_REJECT)
                .action(actions.manualRejectAction()).and()

                // 申诉阶段
                .withExternal().source(ImageReviewState.FINAL_REJECTED).target(ImageReviewState.APPEAL_PENDING)
                .event(ImageReviewEvent.APPEAL_SUBMIT).and()

                .withExternal().source(ImageReviewState.APPEAL_PENDING).target(ImageReviewState.APPEAL_PASS)
                .event(ImageReviewEvent.APPEAL_PASS).action(actions.appealPassAction()).guard(guards.AppealGuard()).and()

                .withExternal().source(ImageReviewState.APPEAL_PENDING).target(ImageReviewState.APPEAL_REJECTED)
                .event(ImageReviewEvent.APPEAL_REJECT).guard(guards.AppealGuard()).action(actions.appealRejectAction());
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<ImageReviewState, ImageReviewEvent> config) throws Exception {
        config.withConfiguration().autoStartup(true);
    }
}
