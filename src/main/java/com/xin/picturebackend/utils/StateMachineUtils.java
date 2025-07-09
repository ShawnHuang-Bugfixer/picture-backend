package com.xin.picturebackend.utils;

import com.xin.picturebackend.service.statemachine.events.ImageReviewEvent;
import com.xin.picturebackend.service.statemachine.states.ImageReviewState;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;

/**
 *
 * @author 黄兴鑫
 * @since 2025/7/8 17:12
 */
public class StateMachineUtils {
    public static StateMachine<ImageReviewState, ImageReviewEvent> getStateMachine(StateMachineFactory<ImageReviewState, ImageReviewEvent> stateMachineFactory, ImageReviewState imageReviewState) {
        // 获取状态机工厂并创建状态机实例
        StateMachine<ImageReviewState, ImageReviewEvent> stateMachine = stateMachineFactory.getStateMachine();

        // 设置初始状态
        stateMachine.getStateMachineAccessor().doWithAllRegions(access -> {
            access.resetStateMachine(new DefaultStateMachineContext<>(imageReviewState, // 你想要设置的初始状态
                    null, null, null, null, stateMachine.getId()));
        });

        return stateMachine;
    }

    public static void setStateMachineExtendedState(StateMachine<ImageReviewState, ImageReviewEvent> stateMachine, String key, Object value) {
        stateMachine.getExtendedState().getVariables().put(key, value);
    }
}
