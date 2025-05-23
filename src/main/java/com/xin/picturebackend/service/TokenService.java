package com.xin.picturebackend.service;

import com.xin.picturebackend.token.RefreshToken;

import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;

public interface TokenService {
    @Deprecated
    boolean checkJWTSessionInRedis(Long loginId, String jti);

    RefreshToken checkAndGetRefreshTokenObj(String refreshToken);

    void removeCookie(HttpServletResponse response, String cookieName, String path, String domain);

    RefreshToken createRefreshTokenObj(Long userId, String jti);

    String generateRefreshToken();

    void storeRefreshTokenToRedis(String refreshToken, RefreshToken refreshTokenObj);

    void writeTokenToCookies(HttpServletResponse response, String token, String configKey);

    RefreshToken createNewRefreshTokenBasedOnOld(@NotNull RefreshToken old, @NotNull String jti);

    @Deprecated
    boolean removeRefreshTokenFromRedis(String refreshToken);

    @Deprecated
    boolean EqualToJtiFromRefreshToken(String refreshToken, String jti);

    @Deprecated
    void storeJWTToRedis(Long userId, String jti);

    void buildReverseIndex(Long id, String refreshToken);

    boolean removeRefreshTokenAndReverseIndex(String refreshToken, Long userId);

    void removeRefreshTokenAndReverseIndex(Long userId);

    void addIntoBlackList(String jti, long userId);

    boolean notInJWTBlacklist(String jti, Long userId);

    void checkLoginState(Long id);
}
