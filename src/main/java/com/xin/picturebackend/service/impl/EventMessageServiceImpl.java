package com.xin.picturebackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xin.picturebackend.model.entity.EventMessage;
import com.xin.picturebackend.service.EventMessageService;
import com.xin.picturebackend.mapper.EventMessageMapper;
import org.springframework.stereotype.Service;

/**
* @author Lenovo
* @description 针对表【event_message(活动消息模板表)】的数据库操作Service实现
* @createDate 2025-06-30 09:56:13
*/
@Service
public class EventMessageServiceImpl extends ServiceImpl<EventMessageMapper, EventMessage>
    implements EventMessageService{

}




