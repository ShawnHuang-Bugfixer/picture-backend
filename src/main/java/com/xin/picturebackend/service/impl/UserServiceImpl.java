package com.xin.picturebackend.service.impl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.SaLoginConfig;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import com.xin.picturebackend.model.dto.user.UserQueryRequest;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.enums.UserRoleEnum;
import com.xin.picturebackend.model.vo.LoginUserVO;
import com.xin.picturebackend.model.vo.UserVO;
import com.xin.picturebackend.service.TokenService;
import com.xin.picturebackend.service.UserService;
import com.xin.picturebackend.mapper.UserMapper;
import com.xin.picturebackend.token.RefreshToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Lenovo
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {
    @Value("${app.cookies.cookieConfigs.refreshToken.name}")
    private String refreshTokenName;

    @Value("${app.cookies.cookieConfigs.refreshToken.path}")
    private String path;

    @Resource
    private TokenService tokenService;

    @Resource
    private StpLogic stpLogicJwtForStateless;

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 参数校验
        // 2. 用户名重复校验
        // 3. 密码加密
        // 4. 数据库插入数据
        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount, userPassword, checkPassword), ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtils.throwIf(userAccount.length() < 4, ErrorCode.PARAMS_ERROR, "用户账号过短");
        ThrowUtils.throwIf(userPassword.length() < 8 || checkPassword.length() < 8, ErrorCode.PARAMS_ERROR, "密码过短");
        ThrowUtils.throwIf(!userPassword.equals(checkPassword), ErrorCode.PARAMS_ERROR, "密码不一致");
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        Long l = baseMapper.selectCount(queryWrapper);
        ThrowUtils.throwIf(l > 0, ErrorCode.PARAMS_ERROR, "账号重复");
        User user = new User();
        user.setUserName("默认名称");
        user.setUserRole(UserRoleEnum.USER.getValue());
        user.setUserAccount(userAccount);
        user.setUserPassword(getEncryptPassword(userPassword));
        int insert = baseMapper.insert(user);
        ThrowUtils.throwIf(insert < 1, ErrorCode.SYSTEM_ERROR, "注册失败，数据库异常");
        return user.getId();
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request, HttpServletResponse response) {
        // 1. 参数校验
        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount, userPassword), ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtils.throwIf(userAccount.length() < 4, ErrorCode.PARAMS_ERROR, "用户名错误");
        ThrowUtils.throwIf(userPassword.length() < 8, ErrorCode.PARAMS_ERROR, "密码错误");
        // 2. 数据库校验
        String encryptPassword = getEncryptPassword(userPassword);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = baseMapper.selectOne(queryWrapper);
        if (user == null) {
            log.error("user login fail, userAccount:{}, userPassword:{}", userAccount, userPassword);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名或密码错误！");
        }
        // 3. 生成 JWT，默认将 JWT 写入 Cookie;
        String jti = IdUtil.randomUUID();
        StpUtil.login(user.getId(), SaLoginConfig
                .setExtra("jti", jti));
        // 4. 生成 RefreshToken，将 RefreshToken 写入 redis。
        RefreshToken refreshTokenObj = tokenService.createRefreshTokenObj(user.getId(), jti);
        String refreshToken = tokenService.generateRefreshToken();
        tokenService.storeRefreshTokenToRedis(refreshToken, refreshTokenObj);
        // 5. 将 RefreshToken 写入到 cookie
        tokenService.writeRefreshTokenToCookies(response, refreshToken);
        // 6. 建立反向索引 user_refresh_token:{userId} → refreshToken
        tokenService.buildReverseIndex(user.getId(), refreshToken);
        return this.getLoginVO(user);
    }

    @Override
    public LoginUserVO getLoginVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        if (loginUserVO.getUserRole().equals("system-admin")) {
            loginUserVO.setUserRole("admin");
        }
        return loginUserVO;
    }

    @Override
    @SaCheckLogin
    public User getLoginUser(HttpServletRequest request) {
        // todo 先判断 jwt 是否是黑名单成员
        Long loginId = StpUtil.getLoginIdAsLong();
        String jti = StpUtil.getExtra("jti").toString();
        User fetchedUser = null;
        if (tokenService.notInJWTBlacklist(jti, loginId)) {
            fetchedUser = this.getById(loginId);
        }
        ThrowUtils.throwIf(fetchedUser == null, ErrorCode.NOT_LOGIN_ERROR);
        return fetchedUser;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }


    @Override
    public String getEncryptPassword(String password) {
        final String salt = "ShawnHuang";
        return DigestUtils.md5DigestAsHex((password + salt).getBytes());
    }

    @Override
    public boolean userLogout(HttpServletRequest request, HttpServletResponse response) {
        String jti = StpUtil.getExtra("jti").toString();
        long userId = StpUtil.getLoginIdAsLong();
        // 1. 把 jti 加入 redis 黑名单 jwt_blacklist:{userId}
        tokenService.addIntoBlackList(jti, userId);
        // 2. 从 redis 中清除 user_refresh_token:{userId}
        // 3. 从 redis 中清除 refresh_token:{refreshToken}
        tokenService.removeRefreshTokenAndReverseIndex(userId);
        // 4. 清除 cookie
        StpUtil.logout();
        tokenService.removeCookie(response, refreshTokenName, path);
        return true;
    }

    @Override
    public boolean refreshJWT(HttpServletRequest request, HttpServletResponse response) {
        // 获取 Cookie 信息
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            log.info("cookies is empty");
            return false;
        }
        // 查找 Refresh Token
        String refreshToken = null;
        for (Cookie cookie : cookies) {
            if (refreshTokenName.equals(cookie.getName())) {
                refreshToken = cookie.getValue();
            }
        }

        // 3. 利用 refreshToken 请求新的 JWT。
        RefreshToken fetchRefreshTokenObj = tokenService.checkAndGetRefreshTokenObj(refreshToken);
        if (fetchRefreshTokenObj == null) {
            log.info("illegal refreshToken!");
            return false;
        }
        // 删除 Redis 中旧 refreshToken, 删除反向索引
        boolean success = tokenService.removeRefreshTokenAndReverseIndex(refreshToken, fetchRefreshTokenObj.getUserId());
        if (!success) {
            log.info("operate redis fail!");
            return false;
        }
        // 构建新的 JWT 和 refreshToken 并写入 response cookies
        String jti = IdUtil.randomUUID();
        SaLoginModel saLoginModel = SaLoginConfig.setExtra("jti", jti);
        String newJWT = stpLogicJwtForStateless.createLoginSession(fetchRefreshTokenObj.getUserId(), saLoginModel);
        stpLogicJwtForStateless.setTokenValue(newJWT, saLoginModel); // 自动根据 saToken 配置写入指定位置
        refreshToken = tokenService.generateRefreshToken();
        RefreshToken newRefreshTokenObj = tokenService.createNewRefreshTokenBasedOnOld(fetchRefreshTokenObj, jti);
        tokenService.writeRefreshTokenToCookies(response, refreshToken);
        // 将新 refreshToken 和 新 反向索引 写入 Redis
        tokenService.storeRefreshTokenToRedis(refreshToken, newRefreshTokenObj);
        tokenService.buildReverseIndex(fetchRefreshTokenObj.getUserId(), refreshToken);
        return true;
    }

    /**
     * 构造 QueryWrapper 拼接 sql
     *
     * @param userQueryRequest 管理员查询请求模型
     * @return 返回拼接后的 QueryWrapper<User>
     */
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && user.getUserRole().equals(UserRoleEnum.ADMIN.getValue());
    }
}




