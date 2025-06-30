package com.xin.picturebackend;

import com.xin.picturebackend.messagepush.eventpublisher.EventPublisher;
import com.xin.picturebackend.messagepush.model.ReviewMessage;
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
        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(() -> {
                eventPublisher.publishMessage(new ReviewMessage(1L, "test 这是测试, 来自线程：" + Thread.currentThread().getName()));
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
