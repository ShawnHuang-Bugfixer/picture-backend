package com.xin.picturebackend.controller;

import com.xin.picturebackend.common.BaseResponse;
import com.xin.picturebackend.common.ResultUtils;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.model.dto.message.ACKReviewMessage;
import com.xin.picturebackend.model.entity.ReviewMessage;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.service.ReviewMessageService;
import com.xin.picturebackend.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 *
 * @author 黄兴鑫
 * @since 2025/6/30 14:51
 */
@RequestMapping("/message")
@RestController
public class MessageController {
    @Resource
    private ReviewMessageService reviewMessageService;
    @Resource
    private UserService userService;

    @PostMapping("/ack/review")
    private BaseResponse<Boolean> setReviewStatus (@RequestBody ACKReviewMessage ack, HttpServletRequest request) {
        Long userId = ack.getUserId();
        Long msgId = ack.getMsgId();
        User loginUser = userService.getLoginUser(request);
        if (!userId.equals(loginUser.getId())) throw new BusinessException(ErrorCode.OPERATION_ERROR, "不能修改他人内容");
        reviewMessageService.markAsRead(msgId);
        return ResultUtils.success(true);
    }

    @GetMapping("/unread/num")
    private BaseResponse<Long> getUnreadMessageNum(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long num = reviewMessageService.getUnreadMessageNum(loginUser.getId());
        return ResultUtils.success(num);
    }

    /**
     * 获取当前用户的未读消息列表
     */
    @GetMapping("/unread/list")
    public BaseResponse<List<ReviewMessage>> getUnreadMessageList(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        List<ReviewMessage> unreadMessages = reviewMessageService.getUnreadMessages(loginUser.getId());
        return ResultUtils.success(unreadMessages);
    }

}
