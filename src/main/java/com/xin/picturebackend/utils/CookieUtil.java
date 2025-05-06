package com.xin.picturebackend.utils;

import cn.hutool.core.util.StrUtil;
import com.xin.picturebackend.config.CookiesProperties;

import javax.servlet.http.Cookie;

/**
 *
 * @author 黄兴鑫
 * @since 2025/4/28 17:16
 */
public class CookieUtil {

    /**
     * 根据 CookieInfo 创建一个标准的 javax.servlet.http.Cookie
     *
     * @param info 你的 CookieInfo 配置对象
     * @return Cookie
     */
    public static Cookie buildCookie(CookiesProperties.CookieInfo info, String value) {
        String trueValue = StrUtil.isEmpty(value) ? info.getValue() : value;
        Cookie cookie = new Cookie(info.getName(), trueValue);
        cookie.setComment(info.getComment());
        cookie.setDomain(info.getDomain());
        cookie.setMaxAge(info.getMaxAge().intValue()); // Cookie 需要 int
        cookie.setPath(info.getPath());
        cookie.setSecure(info.getSecure());
        cookie.setHttpOnly(info.getHttpOnly());
        return cookie;
    }

    /**
     * 手动构造 Set-Cookie 头，支持 SameSite 属性（Spring Boot 2 的 Cookie 没有 SameSite）
     *
     * @param cookie   Cookie 对象
     * @param sameSite SameSite 策略（比如 Lax、Strict、None）
     * @return Set-Cookie 字符串
     */
    public static String buildSetCookieHeader(Cookie cookie, String sameSite) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookie.getName()).append("=").append(cookie.getValue()).append("; ");
        sb.append("Path=").append(cookie.getPath()).append("; ");
        if (cookie.getDomain() != null && !cookie.getDomain().isEmpty()) {
            sb.append("Domain=").append(cookie.getDomain()).append("; ");
        }
        if (cookie.getMaxAge() >= 0) {
            sb.append("Max-Age=").append(cookie.getMaxAge()).append("; ");
        }
        if (cookie.getSecure()) {
            sb.append("Secure; ");
        }
        if (cookie.isHttpOnly()) {
            sb.append("HttpOnly; ");
        }
        if (sameSite != null && !sameSite.isEmpty()) {
            sb.append("SameSite=").append(sameSite).append("; ");
        }
        // 去掉最后一个分号和空格
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }
}

