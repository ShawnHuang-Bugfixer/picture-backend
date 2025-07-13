package com.xin.picturebackend.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

/**
 * @author 黄兴鑫
 * @since 2025/7/13 16:49
 */
@SpringBootTest
public class EmailCodeServiceTest {
    @Resource
    private EmailCodeService emailCodeService;

    @Test
    void testSendCode() {
        String targetMail = "15298262348@163.com";
        emailCodeService.sendCode(targetMail);
    }

    @Test
    void testWrongMail() {
        String wrongTargetMail = "15298262348163.com";
        emailCodeService.sendCode(wrongTargetMail);
    }

    @Test
    void testRegisteredMail() {
        String targetMail = "15298262348@163.com";
        emailCodeService.sendCode(targetMail);
    }

    @Test
    void testCooldown() {
        String targetMail = "15298262348@163.com";
        emailCodeService.sendCode(targetMail);
        try {
            Thread.sleep(2000);
            emailCodeService.sendCode(targetMail);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testLimitFrequency() {
        for (int i = 0; i < 3; i++) {
            String targetMail = "15298262348@163.com";
            emailCodeService.sendCode(targetMail);
            try {
                Thread.sleep(25000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
