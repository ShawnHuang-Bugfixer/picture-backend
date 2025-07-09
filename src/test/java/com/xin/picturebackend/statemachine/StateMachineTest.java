package com.xin.picturebackend.statemachine;

import com.xin.picturebackend.model.entity.Picture;
import com.xin.picturebackend.service.statemachine.context.ContextKey;
import com.xin.picturebackend.service.statemachine.events.ImageReviewEvent;
import com.xin.picturebackend.service.statemachine.states.ImageReviewState;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;

import javax.annotation.Resource;
import java.util.ArrayList;

import static com.xin.picturebackend.utils.StateMachineUtils.getStateMachine;

/**
 * 测试图片审核状态机基本使用
 *
 * @author 黄兴鑫
 * @since 2025/7/4 14:04
 */
@SpringBootTest
public class StateMachineTest {
    @Resource
    private StateMachineFactory<ImageReviewState, ImageReviewEvent> stateMachineFactory;

    @Test
    void testAIPassProcess() {
        StateMachine<ImageReviewState, ImageReviewEvent> stateMachine = getStateMachine(stateMachineFactory, ImageReviewState.PENDING_REVIEW);
        stateMachine.start();
        System.out.println(stateMachine.getState().getId());
        System.out.println(stateMachine.getState().getId());
        Picture picture = new Picture();
        picture.setId(1906366833817010177L);
        picture.setUserId(1894627889584680961L);
        stateMachine.getExtendedState().getVariables().put(ContextKey.PICTURE_OBJ_KEY, picture);
        stateMachine.sendEvent(ImageReviewEvent.AI_REVIEW_PASS);
        stateMachine.stop();
    }

    @Test
    void testAIRejectProcess() {
        ArrayList<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Thread thread = new Thread(() -> {
                StateMachine<ImageReviewState, ImageReviewEvent> stateMachine = getStateMachine(stateMachineFactory, ImageReviewState.PENDING_REVIEW);
                stateMachine.start();
                System.out.println(stateMachine.getState().getId());
                Picture picture = new Picture();
                picture.setId(1906366833817010177L);
                picture.setUserId(1894627889584680961L);
                stateMachine.getExtendedState().getVariables().put(ContextKey.PICTURE_OBJ_KEY, picture);
                stateMachine.sendEvent(ImageReviewEvent.AI_REVIEW_REJECT);
                System.out.println(stateMachine.getState().getId());
                stateMachine.stop();
            }, "th--" + i);
            threads.add(thread);
        }

        threads.forEach(Thread::start);

        while (!threads.isEmpty()) {
            threads.removeIf(next -> next.getState().equals(Thread.State.TERMINATED));
            Thread.yield();
        }
    }

    @Test
    void testAISuspicious() {
        StateMachine<ImageReviewState, ImageReviewEvent> stateMachine = getStateMachine(stateMachineFactory, ImageReviewState.PENDING_REVIEW);
        stateMachine.start();
        System.out.println(stateMachine.getState().getId());
        Picture picture = new Picture();
        picture.setId(1906366833817010177L);
        picture.setUserId(1894627889584680961L);
        stateMachine.getExtendedState().getVariables().put(ContextKey.PICTURE_OBJ_KEY, picture);
        stateMachine.sendEvent(ImageReviewEvent.AI_REVIEW_SUSPICIOUS);
        System.out.println(stateMachine.getState().getId());
        stateMachine.stop();
    }

    @Test
    void testManualReviewPass() {
        StateMachine<ImageReviewState, ImageReviewEvent> stateMachine = getStateMachine(stateMachineFactory, ImageReviewState.PENDING_REVIEW);
        stateMachine.start();
        System.out.println(stateMachine.getState().getId());
        stateMachine.sendEvent(ImageReviewEvent.AI_REVIEW_SUSPICIOUS);
        System.out.println(stateMachine.getState().getId());
        Picture picture = new Picture();
        picture.setId(1906369430506307586L);
        picture.setUserId(1894627889584680961L);
        stateMachine.getExtendedState().getVariables().put(ContextKey.PICTURE_OBJ_KEY, picture);
        stateMachine.getExtendedState().getVariables().put(ContextKey.REVIEWER_ID_KEY, 13465L);
        stateMachine.sendEvent(ImageReviewEvent.MANUAL_REVIEW_PASS);
        System.out.println(stateMachine.getState().getId());
        stateMachine.stop();
    }

    @Test
    void testManualReviewReject() {
        ArrayList<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Thread thread = new Thread(() -> {
                StateMachine<ImageReviewState, ImageReviewEvent> stateMachine = getStateMachine(stateMachineFactory, ImageReviewState.PENDING_REVIEW);
                stateMachine.start();
                System.out.println(stateMachine.getState().getId());
                stateMachine.sendEvent(ImageReviewEvent.AI_REVIEW_SUSPICIOUS);
                System.out.println(stateMachine.getState().getId());
                Picture picture = new Picture();
                picture.setId(1906366833817010177L);
                picture.setUserId(1894627889584680961L);
                stateMachine.getExtendedState().getVariables().put(ContextKey.PICTURE_OBJ_KEY, picture);
                stateMachine.getExtendedState().getVariables().put(ContextKey.REVIEWER_ID_KEY, 13465L);
                stateMachine.sendEvent(ImageReviewEvent.MANUAL_REVIEW_REJECT);
                System.out.println(stateMachine.getState().getId());
                stateMachine.stop();
            }, "th--" + i);
            threads.add(thread);
        }
        threads.forEach(Thread::start);
        while (!threads.isEmpty()) {
            threads.removeIf((thread) -> thread.getState().equals(Thread.State.TERMINATED));
            Thread.yield();
        }
    }

    @Test
    void testAppealPass() {
        StateMachine<ImageReviewState, ImageReviewEvent> stateMachine = getStateMachine(stateMachineFactory, ImageReviewState.FINAL_REJECTED);
        stateMachine.start();
        System.out.println(stateMachine.getState().getId());
        stateMachine.sendEvent(ImageReviewEvent.APPEAL_SUBMIT);
        System.out.println(stateMachine.getState().getId());
        Picture picture = new Picture();
        picture.setId(1906366833817010177L);
        picture.setUserId(1894627889584680961L);
        stateMachine.getExtendedState().getVariables().put(ContextKey.PICTURE_OBJ_KEY, picture);
        stateMachine.getExtendedState().getVariables().put(ContextKey.REVIEWER_ID_KEY, 13465L); // 审核人 id
        stateMachine.sendEvent(ImageReviewEvent.APPEAL_PASS);
        System.out.println(stateMachine.getState().getId());
        stateMachine.stop();
    }

    @Test
    void testAppealReject() {
        StateMachine<ImageReviewState, ImageReviewEvent> stateMachine = getStateMachine(stateMachineFactory, ImageReviewState.FINAL_REJECTED);
        stateMachine.start();
        System.out.println(stateMachine.getState().getId());
        stateMachine.sendEvent(ImageReviewEvent.APPEAL_SUBMIT);
        System.out.println(stateMachine.getState().getId());
        Picture picture = new Picture();
        picture.setId(1906366833817010177L);
        picture.setUserId(1894627889584680961L);
        stateMachine.getExtendedState().getVariables().put(ContextKey.PICTURE_OBJ_KEY, picture);
        stateMachine.getExtendedState().getVariables().put(ContextKey.REVIEWER_ID_KEY, 13465L); // 审核人 id
        stateMachine.sendEvent(ImageReviewEvent.APPEAL_REJECT);
        System.out.println(stateMachine.getState().getId());
        stateMachine.stop();
    }

}
