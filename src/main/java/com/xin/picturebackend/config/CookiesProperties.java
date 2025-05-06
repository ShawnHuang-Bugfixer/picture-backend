package com.xin.picturebackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.servlet.http.Cookie;
import java.util.HashMap;

/**
 * 读取自定义的 cookie
 *
 * @author 黄兴鑫
 * @since 2025/4/28 15:15
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.cookies")
public class CookiesProperties {

    private Map<String, CookieInfo> cookieConfigs = new HashMap<>();

    @Data
    public static class CookieInfo {
        private String name;
        private String comment = "";
        private String domain = "";
        private Long maxAge = 86400L; // 默认1天
        private String path = "/";
        private Boolean secure = false;
        private String value = "";
        private Integer version = 1;
        private Boolean httpOnly = false;
        private String sameSite = "Lax"; // 默认 Lax 策略
    }

    @PostConstruct
    public void initDefaults() {
        // 保证如果 CookieInfo 为空字段，也能补上默认值
        if (cookieConfigs != null) {
            for (Map.Entry<String, CookieInfo> entry : cookieConfigs.entrySet()) {
                CookieInfo cookie = entry.getValue();
                if (cookie.getComment() == null) cookie.setComment("");
                if (cookie.getDomain() == null) cookie.setDomain("");
                if (cookie.getMaxAge() == null) cookie.setMaxAge(86400L);
                if (cookie.getPath() == null) cookie.setPath("/");
                if (cookie.getSecure() == null) cookie.setSecure(false);
                if (cookie.getValue() == null) cookie.setValue("");
                if (cookie.getVersion() == null) cookie.setVersion(1);
                if (cookie.getHttpOnly() == null) cookie.setHttpOnly(false);
                if (cookie.getSameSite() == null) cookie.setSameSite("Lax");
            }
        }
    }
}




