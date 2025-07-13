package com.xin.picturebackend.service.impl;

import com.xin.picturebackend.config.custom.EmailCodeProperties;
import com.xin.picturebackend.config.rabbitmq.MQConstants;
import com.xin.picturebackend.constant.RedisKeyConstant;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.model.messagequeue.email.EmailMessage;
import com.xin.picturebackend.service.EmailCodeService;
import com.xin.picturebackend.service.UserService;
import com.xin.picturebackend.utils.CodeUtil;
import com.xin.picturebackend.utils.EmailUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;

/**
 * 获取用户注册时邮箱验证码
 * @author 黄兴鑫
 * @since 2025/7/13 8:06
 */
@Service
@Slf4j
public class EmailCodeServiceImpl implements EmailCodeService {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private UserService userService;

    @Resource
    private EmailCodeProperties emailCodeProperties;

    @Override
    public boolean sendCode(String email) {
        int maxSends = emailCodeProperties.getMaxSendsPerPeriod();
        int limitExpire = emailCodeProperties.getSendLimitExpireMinutes();
        int length = emailCodeProperties.getCodeLength();
        int cooldown = emailCodeProperties.getSendCooldownSeconds();
        int ttl = emailCodeProperties.getCodeExpireMinutes();

        // 1. 校验邮箱格式
        if (!EmailUtil.isValidEmail(email)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱格式非法");
        }

        // 2. 校验是否已注册
        if (userService.isEmailRegistered(email)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该邮箱已注册");
        }

        // 3. 冷却限制（60s 内不可重复发送）
        String cooldownKey =  RedisKeyConstant.EMAIL_SEND_COOLDOWN_KEY_PREFIX + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "请勿频繁获取验证码");
        }

        // 4. 每 30 分钟内最多发送次数限制
        String frequencyKey =  RedisKeyConstant.EMAIL_SEND_LIMIT_KEY_PREFIX + email;
        Boolean hasFrequencyKey = redisTemplate.hasKey(frequencyKey);
        Long sendCount = redisTemplate.opsForValue().increment(frequencyKey);
        if (Boolean.FALSE.equals(hasFrequencyKey)) {
            redisTemplate.expire(frequencyKey, Duration.ofMinutes(limitExpire));
        }
        if (sendCount != null && sendCount > maxSends) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码发送过于频繁");
        }

        // 5. 生成并缓存验证码（5 分钟有效）
        String code = CodeUtil.generateCode(length);
        String codeKey = RedisKeyConstant.EMAIL_CODE_KEY_PREFIX + email;
        redisTemplate.opsForValue().set(codeKey, code, Duration.ofMinutes(ttl));

        // 设置 60 秒冷却期
        redisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(cooldown));

        // 6. 投递到 MQ
        EmailMessage emailMessage = new EmailMessage(email, code);
        try {
            rabbitTemplate.convertAndSend(MQConstants.EMAIL_CODE_EXCHANGE, MQConstants.ROUTING_EMAIL_CODE, emailMessage);
            log.info("验证码消息已成功投递至 MQ，email={}，code={}", email, code);
        } catch (Exception e) {
            log.error("验证码发送消息投递 MQ 失败，email={}，code={}", email, code, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "验证码发送失败，请稍后再试");
        }

        return true;
    }
}

