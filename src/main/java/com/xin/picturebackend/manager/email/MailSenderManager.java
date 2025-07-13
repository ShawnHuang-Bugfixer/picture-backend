package com.xin.picturebackend.manager.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.UUID;
import org.thymeleaf.context.Context;


/**
 *
 * @author 黄兴鑫
 * @since 2025/7/11 10:40
 */
@Component
public class MailSenderManager {
    @Resource
    private JavaMailSenderImpl mailSender;

    @Resource
    private TemplateEngine templateEngine;

    @Value("${spring.mail.username}") // 正确注入发件人
    private String fromUser;

    @Value("${app.email-code.code-expire-minutes}")
    private int ttl;

    @Value("${app.name}")
    private String appName;

    public boolean sendEmailCode(String email, String code) throws MessagingException {
        Context context = new Context();
        context.setVariable("webName", appName);
        context.setVariable("code", code);
        context.setVariable("expire", ttl); // 或动态传入 ttl
        String htmlContent = templateEngine.process("email_code_template.html", context);

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

        // 设置邮件正文（支持 HTML）
        helper.setText(htmlContent ,true);

        // 设置邮件标题
        helper.setSubject(appName + "-验证码");

        // 设置收件人和发件人（注意顺序）
        helper.setTo(email);
        helper.setFrom(fromUser);

        // 发送邮件
        mailSender.send(mimeMessage);
        return true;
    }

    private String randomCode(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }
}
