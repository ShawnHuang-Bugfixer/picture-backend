package com.xin.picturebackend.service;

import com.xin.picturebackend.model.entity.UserAppealQuota;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author Lenovo
* @description 针对表【user_appeal_quota(用户申诉配额表)】的数据库操作Service
* @createDate 2025-07-07 09:15:03
*/
public interface UserAppealQuotaService extends IService<UserAppealQuota> {

    void increaseAppeal(Long userId);

    void decreaseAppeal(Long userId);

    boolean canAppeal(Long userId);
}
