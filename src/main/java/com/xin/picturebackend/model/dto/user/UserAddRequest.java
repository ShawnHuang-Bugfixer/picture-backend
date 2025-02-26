package com.xin.picturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * TODO 管理员新增用户数据模型
 *
 * @author 黄兴鑫
 * @since 2025/2/26 15:15
 */
@Data
public class UserAddRequest implements Serializable {

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色: user, admin
     */
    private String userRole;

    private static final long serialVersionUID = 1L;
}

