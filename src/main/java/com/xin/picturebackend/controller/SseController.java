package com.xin.picturebackend.controller;

import cn.hutool.json.JSONUtil;
import com.xin.picturebackend.manager.UserConnectionManager;
import com.xin.picturebackend.manager.connections.ConnectionType;
import com.xin.picturebackend.messagepush.model.MessageInfo;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.service.ReviewMessageService;
import com.xin.picturebackend.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

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

    @Resource
    private ReviewMessageService reviewMessageService;

    @Resource
    private UserService userService;

    @GetMapping("/sse/connect")
    public SseEmitter connect(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        SseEmitter emitter = new SseEmitter(0L); // 永不超时

        boolean success = connectionManager.register(userId, ConnectionType.SSE, emitter);

        emitter.onCompletion(() -> connectionManager.unregister(userId));
        emitter.onTimeout(() -> connectionManager.unregister(userId));
        emitter.onError((e) -> connectionManager.unregister(userId));

        if (success) {
            Long unreadMessageNum = reviewMessageService.getUnreadMessageNum(userId);
            MessageInfo messageInfo = new MessageInfo();
            messageInfo.setInfo(unreadMessageNum.toString());
            String responseJson = JSONUtil.toJsonStr(messageInfo);
            try {
                emitter.send(responseJson, MediaType.APPLICATION_JSON);
            } catch (IOException e) {
                connectionManager.unregister(userId);
            }
        }

        return emitter;
    }

}
