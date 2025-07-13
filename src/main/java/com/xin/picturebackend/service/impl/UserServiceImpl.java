package com.xin.picturebackend.service.impl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.SaLoginConfig;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xin.picturebackend.common.BaseResponse;
import com.xin.picturebackend.common.ResultUtils;
import com.xin.picturebackend.config.CookiesProperties;
import com.xin.picturebackend.config.custom.EmailCodeProperties;
import com.xin.picturebackend.constant.RedisKeyConstant;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import com.xin.picturebackend.model.dto.user.UserQueryRequest;
import com.xin.picturebackend.model.dto.user.UserUpdateRequest;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.entity.UserEmail;
import com.xin.picturebackend.model.enums.UserRoleEnum;
import com.xin.picturebackend.model.vo.LoginUserVO;
import com.xin.picturebackend.model.vo.UserVO;
import com.xin.picturebackend.service.TokenService;
import com.xin.picturebackend.service.UserEmailService;
import com.xin.picturebackend.service.UserService;
import com.xin.picturebackend.mapper.UserMapper;
import com.xin.picturebackend.token.RefreshToken;
import com.xin.picturebackend.utils.CookieUtil;
import com.xin.picturebackend.utils.EmailUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Lenovo
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Resource
    private TokenService tokenService;

    @Resource
    private StpLogic stpLogicJwtForStateless;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CookiesProperties cookiesProperties;

    @Resource
    private UserEmailService userEmailService;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private EmailCodeProperties emailCodeProperties;

    private String domain;

    private String refreshTokenName;

    private String path;

    @PostConstruct
    public void init() {
        CookiesProperties.CookieInfo refreshTokenInfo = cookiesProperties.getCookieConfigs().get("refreshToken");
        Cookie refreshCookie = CookieUtil.buildCookie(refreshTokenInfo, null);
        refreshTokenName = refreshCookie.getName();
        path = refreshCookie.getPath();
        domain = refreshCookie.getDomain();
    }

    @Override
    @Transactional
    public long userRegister(String userAccount, String userPassword, String checkPassword, String email, String code) {
        // 1. 格式校验
        checkRegistrationLegality(userAccount, userPassword, checkPassword, code, email);
        String codeKey = RedisKeyConstant.EMAIL_CODE_KEY_PREFIX + email;
        String failKey = RedisKeyConstant.EMAIL_CODE_FAIL_COUNT_PREFIX + email;

        // 2. 验证码校验
        validateCode(codeKey, failKey, email, code);

        // 3. 用户注册入库
        Long userId = createUser(userAccount, userPassword, email);

        // 4. 清理 Redis 中验证码相关记录
        redisTemplate.delete(Arrays.asList(codeKey, failKey,
                RedisKeyConstant.EMAIL_SEND_COOLDOWN_KEY_PREFIX + email,
                RedisKeyConstant.EMAIL_SEND_LIMIT_KEY_PREFIX + email));


        return userId;
    }

    private void validateCode(String codeKey, String failKey, String email, String code) {
        // 1. 获取验证码
        String cachedCode = redisTemplate.opsForValue().get(codeKey);

        // 1.1 校验失败次数
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        int failCount = failCountStr != null ? Integer.parseInt(failCountStr) : 0;
        if (failCount >= emailCodeProperties.getMaxValidateFailCount()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码错误次数过多，请稍后再试");
        }

        // 1.2 验证码不存在
        if (cachedCode == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码已过期或未发送");
        }

        // 1.3 验证码错误
        if (!cachedCode.equals(code)) {
            redisTemplate.opsForValue().increment(failKey);
            // 可选：设置失败计数过期时间，如 30 分钟
            redisTemplate.expire(failKey, Duration.ofMinutes(emailCodeProperties.getValidateFailCountExpireMinutes()));
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码错误");
        }
    }

    @Transactional
    public Long createUser(String userAccount, String userPassword, String email) {
        User user = new User();
        user.setUserName(userAccount);
        user.setUserRole(UserRoleEnum.USER.getValue());
        user.setUserAccount(userAccount);
        user.setUserPassword(getEncryptPassword(userPassword));
        int insert = baseMapper.insert(user);
        ThrowUtils.throwIf(insert < 1, ErrorCode.SYSTEM_ERROR, "注册失败，请稍后再试！");
        UserEmail userEmail = new UserEmail();
        userEmail.setEmail(email);
        userEmail.setUserId(user.getId());
        userEmail.setCreatedAt(new Date());
        boolean save = userEmailService.save(userEmail);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "注册失败，请稍后再试");
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
        // 2.1 校验用户是否已经登录。
        tokenService.checkLoginState(user.getId());
        // 3. 生成 JWT，默认将 JWT 写入 Cookie;
        String jti = IdUtil.randomUUID();
        StpUtil.login(user.getId(), SaLoginConfig
                .setExtra("jti", jti));
        // 4. 生成 RefreshToken，将 RefreshToken 写入 redis。
        RefreshToken refreshTokenObj = tokenService.createRefreshTokenObj(user.getId(), jti);
        String refreshToken = tokenService.generateRefreshToken();
        tokenService.storeRefreshTokenToRedis(refreshToken, refreshTokenObj);
        // 5. 将 RefreshToken 写入到 cookie
        tokenService.writeTokenToCookies(response, refreshToken, "refreshToken");
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
        tokenService.removeCookie(response, refreshTokenName, path, domain);
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
        tokenService.writeTokenToCookies(response, refreshToken, "refreshToken");
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

    @Override
    public void getOnceToken(HttpServletResponse response) {
        long userId = StpUtil.getLoginIdAsLong();
        String key = RedisKeyConstant.ONCE_TOKEN_PREFIX + userId;
        ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
        String uuid = UUID.randomUUID().toString();
        // 产生一次性令牌并写入 redis 和 cookie。
        operations.set(key, uuid);
        tokenService.writeTokenToCookies(response, uuid, "onceToken");
        log.debug("成功写入");
    }

    @Override
    public BaseResponse<Boolean> updateUserInfo(UserUpdateRequest userUpdateRequest) {
        StpUtil.checkLogin();
        long loginId = StpUtil.getLoginIdAsLong();
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数错误！");
        }
        if (userUpdateRequest.getId() != loginId) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权修改他人信息！");
        }
        if (userUpdateRequest.getUserRole() != null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权修改用户角色");
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        boolean result = updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @Override
    public boolean isEmailRegistered(String email) {
        QueryWrapper<UserEmail> userEmailQueryWrapper = new QueryWrapper<>();
        userEmailQueryWrapper.eq(StrUtil.isNotBlank(email), "email", email);
        return userEmailService.exists(userEmailQueryWrapper);
    }

    private void checkRegistrationLegality(String userAccount, String userPassword, String checkPassword, String code, String email) {
        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount, userPassword, checkPassword, code), ErrorCode.PARAMS_ERROR, "请求参数错误！");
        ThrowUtils.throwIf(!userPassword.equals(checkPassword), ErrorCode.PARAMS_ERROR, "密码不一致！");
        ThrowUtils.throwIf(userAccount.length() < 4, ErrorCode.PARAMS_ERROR, "用户账号过短！");
        ThrowUtils.throwIf(userPassword.length() < 8, ErrorCode.PARAMS_ERROR, "密码过短！");
        ThrowUtils.throwIf(!EmailUtil.isValidEmail(email), ErrorCode.PARAMS_ERROR, "邮箱格式错误！");
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        Long l = baseMapper.selectCount(queryWrapper);
        ThrowUtils.throwIf(l > 0, ErrorCode.PARAMS_ERROR, "账号重复");
    }
}




