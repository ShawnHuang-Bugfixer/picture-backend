package com.xin.picturebackend.messagepush.eventhandler;

import com.xin.picturebackend.messagepush.model.IMessage;
import com.xin.picturebackend.messagepush.model.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;

/**
 * @author 黄兴鑫
 * @since 2025/6/27 13:54
 */
@Slf4j
@Component
public class EventHandler {
    @Value("${app.retry.event.max}")
    private int maxRetries;
    private int retryCount = 0;
    private boolean success = false;

    @Async("messageEventExecutor")
    @EventListener
    public void onMessageEvent(MessageEvent event) {
        handleMessage(event);
    }

    private void handleMessage(MessageEvent event) {
        while (retryCount < maxRetries && !success) {
            try {
                IMessage message = event.getMessage();
                success = true; // 标记成功
            } catch (Exception e) {
                retryCount++;
                log.warn("Attempt {} failed: {}", retryCount, e.getMessage());
                sleep(500L * retryCount); // 指数退避
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
