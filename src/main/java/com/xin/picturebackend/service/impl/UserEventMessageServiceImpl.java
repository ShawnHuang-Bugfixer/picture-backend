package com.xin.picturebackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xin.picturebackend.model.entity.UserEventMessage;
import com.xin.picturebackend.service.UserEventMessageService;
import com.xin.picturebackend.mapper.UserEventMessageMapper;
import org.springframework.stereotype.Service;

/**
* @author Lenovo
* @description 针对表【user_event_message(用户接收的活动消息关联表)】的数据库操作Service实现
* @createDate 2025-06-30 09:56:13
*/
@Service
public class UserEventMessageServiceImpl extends ServiceImpl<UserEventMessageMapper, UserEventMessage>
    implements UserEventMessageService{

}




