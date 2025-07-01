package com.xin.picturebackend.meaagepublish;

import com.xin.picturebackend.messagepush.eventpublisher.EventPublisher;
import com.xin.picturebackend.model.entity.ReviewMessage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.ArrayList;

/**
 * @author 黄兴鑫
 * @since 2025/6/27 14:57
 */
@SpringBootTest
@Slf4j
public class EventPublisherTest {
    @Resource
    private EventPublisher eventPublisher;

    @Test
    void test() {
        ArrayList<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            Thread thread = new Thread(() -> {
                ReviewMessage reviewMessage = new ReviewMessage();
                reviewMessage.setUserId(123456L);
                reviewMessage.setContent("message from thread" + Thread.currentThread().getName());
                eventPublisher.publishMessage(reviewMessage);
            }, "thread---" + i);
            threads.add(thread);
            thread.start();
        }
        int count = 0;
        for (int i = 0; ; i++) {
            if (i >= threads.size()) {
                i = 0;
            }
            if (threads.get(i).getState().equals(Thread.State.TERMINATED)) {
                count ++;
            }
            if (count == threads.size()) {
                break;
            }
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
