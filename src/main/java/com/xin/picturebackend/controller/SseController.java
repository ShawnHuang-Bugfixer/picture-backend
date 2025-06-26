package com.xin.picturebackend.controller;

import com.xin.picturebackend.manager.UserConnectionManager;
import com.xin.picturebackend.manager.connections.ConnectionType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;

/**
 * 服务器基于 SSE 的消息推送。
 *
 * @author 黄兴鑫
 * @since 2025/6/26 15:58
 */
@RestController
public class SseController {

    @Resource
    private UserConnectionManager connectionManager;

    @GetMapping("/sse/connect")
    public SseEmitter connect(@RequestParam Long userId) {
        SseEmitter emitter = new SseEmitter(0L); // 永不超时

        connectionManager.register(userId, ConnectionType.SSE, emitter);

        emitter.onCompletion(() -> connectionManager.unregister(userId));
        emitter.onTimeout(() -> connectionManager.unregister(userId));
        emitter.onError((e) -> connectionManager.unregister(userId));
        return emitter;
    }
}
