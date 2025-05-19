package com.xin.picturebackend.constant;

public interface RedisKeyConstant {
    String JWT_KEY_PREFIX = "jwt_session:"; // {userId}
    String REFRESH_TOKEN_KEY_PREFIX = "refresh_token:"; //{refreshToken}
    String REVERSE_INDEX_PREFIX = "user_refresh_token:"; // {userId}
    String JWT_BLACKLIST_PREFIX = "jwt_blacklist:"; // {userId}
    String ONCE_TOKEN_PREFIX = "once_token:"; // {userId}
}
