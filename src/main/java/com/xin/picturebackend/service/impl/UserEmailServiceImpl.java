package com.xin.picturebackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xin.picturebackend.model.entity.UserEmail;
import com.xin.picturebackend.service.UserEmailService;
import com.xin.picturebackend.mapper.UserEmailMapper;
import org.springframework.stereotype.Service;

/**
* @author Lenovo
* @description 针对表【user_email】的数据库操作Service实现
* @createDate 2025-07-13 08:26:35
*/
@Service
public class UserEmailServiceImpl extends ServiceImpl<UserEmailMapper, UserEmail>
    implements UserEmailService{

}




