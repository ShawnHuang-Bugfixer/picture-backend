package com.xin.picturebackend.config.cache;

import com.xin.picturebackend.config.CookiesProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;


/**
 *
 * @author 黄兴鑫
 * @since 2025/4/28 15:57
 */
@SpringBootTest
public class CookiesPropertiesTest {
    @Resource
    private CookiesProperties cookiesProperties;

    @Test
    void shouldLoadConfig() {
        CookiesProperties.CookieInfo refreshToken = cookiesProperties.getCookieConfigs().get("refreshToken");
        System.out.println(refreshToken.getName());
    }
}