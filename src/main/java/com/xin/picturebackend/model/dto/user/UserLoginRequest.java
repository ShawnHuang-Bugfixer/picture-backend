package com.xin.picturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * TODO
 *
 * @author 黄兴鑫
 * @since 2025/2/26 14:00
 */
@Data
public class UserLoginRequest implements Serializable {
    private static final long serialVersionUID = 3191241716373120793L;
    private String userAccount;
    private String userPassword;
}
