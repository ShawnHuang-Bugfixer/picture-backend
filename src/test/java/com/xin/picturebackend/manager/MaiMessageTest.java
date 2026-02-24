package com.xin.picturebackend.manager;

import com.xin.picturebackend.manager.email.MailSenderManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import javax.mail.MessagingException;

/**
 *
 * @author 黄兴鑫
 * @since 2025/7/11 10:56
 */
@SpringBootTest
@Disabled("依赖真实邮箱服务和外网环境，默认测试环境禁用")
public class MaiMessageTest {
    @Resource
    private MailSenderManager mailSenderManager;

    @Test
    void test() {
        String mail = "15298262348@163.com";
        try {
            long begin = System.currentTimeMillis();
            mailSenderManager.sendEmailCode(mail, 123456 + "");
            long end = System.currentTimeMillis();
            System.out.println(end - begin);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}
