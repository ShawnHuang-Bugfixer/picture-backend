package com.xin.picturebackend.config.custom;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 读取自定义邮箱编码配置
 *
 * @author 黄兴鑫
 * @since 2025/7/13 10:41
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.email-code")
public class EmailCodeProperties {
    /** 每单位时间最多发送次数，限制邮箱单位之间内发送验证码次数。 */
    private int maxSendsPerPeriod;

    /** 配置限制邮箱发送验证码次数的单位时间 */
    private int sendLimitExpireMinutes;

    /** 验证码长度 */
    private int codeLength;

    /** 每次发送冷却时间（秒） */
    private int sendCooldownSeconds;

    /** 验证码有效时间（分钟） */
    private int codeExpireMinutes;

    /** 校验验证码错误统计次数的过期时间 */
    private int validateFailCountExpireMinutes;

    private int maxValidateFailCount;
}
