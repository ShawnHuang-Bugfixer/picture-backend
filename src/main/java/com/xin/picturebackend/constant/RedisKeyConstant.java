package com.xin.picturebackend.constant;

public interface RedisKeyConstant {
    // 双 token 身份验证
    String JWT_KEY_PREFIX = "jwt_session:"; // {userId}
    String REFRESH_TOKEN_KEY_PREFIX = "refresh_token:"; //{refreshToken}
    String REVERSE_INDEX_PREFIX = "user_refresh_token:"; // {userId}
    String JWT_BLACKLIST_PREFIX = "jwt_blacklist:"; // {userId}

    // 防重放攻击一次性 token
    String ONCE_TOKEN_LOGIN_PREFIX = "once_token:login:"; // {userId}

    // 邮箱注册验证码
    String EMAIL_CODE_KEY_PREFIX = "email:code:";
    String EMAIL_SEND_LIMIT_KEY_PREFIX = "email:frequency:";
    String EMAIL_SEND_COOLDOWN_KEY_PREFIX = "email:code:cooldown:";
    String EMAIL_CODE_FAIL_COUNT_PREFIX = "email:fail:";
    String ONCE_TOKEN_NOT_LOGIN_PREFIX = "once_token:not_login:";
}
