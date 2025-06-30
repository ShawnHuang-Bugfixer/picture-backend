package com.xin.picturebackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xin.picturebackend.model.entity.ReviewMessage;
import com.xin.picturebackend.service.ReviewMessageService;
import com.xin.picturebackend.mapper.ReviewMessageMapper;
import org.springframework.stereotype.Service;

/**
* @author Lenovo
* @description 针对表【review_message(审核消息表)】的数据库操作Service实现
* @createDate 2025-06-30 09:56:13
*/
@Service
public class ReviewMessageServiceImpl extends ServiceImpl<ReviewMessageMapper, ReviewMessage>
    implements ReviewMessageService{

}




