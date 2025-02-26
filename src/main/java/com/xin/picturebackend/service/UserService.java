package com.xin.picturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xin.picturebackend.model.dto.user.UserQueryRequest;
import com.xin.picturebackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xin.picturebackend.model.vo.LoginUserVO;
import com.xin.picturebackend.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author Lenovo
 * @description 针对表【user(用户)】的数据库操作Service
 * @createDate 2025-02-26 10:49:48
 */
public interface UserService extends IService<User> {
    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    LoginUserVO getLoginVO(User user);

    User getLoginUser(HttpServletRequest request);

    UserVO getUserVO(User user);

    List<UserVO> getUserVOList(List<User> userList);

    String getEncryptPassword(String password);

    boolean userLogout(HttpServletRequest request);

    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);
}
