package com.xin.picturebackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xin.picturebackend.model.entity.ReviewMessage;
import com.xin.picturebackend.service.ReviewMessageService;
import com.xin.picturebackend.mapper.ReviewMessageMapper;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * @author Lenovo
 * @description 针对表【review_message(审核消息表)】的数据库操作Service实现
 * @createDate 2025-06-30 09:56:13
 */
@Service
public class ReviewMessageServiceImpl extends ServiceImpl<ReviewMessageMapper, ReviewMessage>
        implements ReviewMessageService {
    @Override
    public void markAsRead(Long msgId) {
        lambdaUpdate()
            .set(ReviewMessage::getStatus, 1)
            .set(ReviewMessage::getReadAt, new Date())
            .eq(ReviewMessage::getId, msgId)
            .update();
    }

    @Override
    public List<ReviewMessage> getUnreadMessages(Long userId) {
        return lambdaQuery()
                .eq(ReviewMessage::getUserId, userId)
                .eq(ReviewMessage::getStatus, 0)
                .orderByDesc(ReviewMessage::getCreatedAt)
                .list();
    }

    @Override
    public Long getUnreadMessageNum(Long id) {
        return lambdaQuery()
                .eq(ReviewMessage::getUserId, id)
                .eq(ReviewMessage::getStatus, 0)  // 0 = 未读
                .count();
    }
}




