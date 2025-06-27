package com.xin.picturebackend.com.xin.picturebackend;

import com.xin.picturebackend.messagepush.eventpublisher.EventPublisher;
import com.xin.picturebackend.messagepush.model.ReviewMessage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

/**
 * TODO
 *
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
        eventPublisher.publishMessage(new ReviewMessage(1L, "test 这是测试"));
    }
}
