package com.xin.picturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新用户信息模型
 *
 * @author 黄兴鑫
 * @since 2025/2/26 15:16
 */
@Data
public class UserUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;

    private static final long serialVersionUID = 1L;
}

