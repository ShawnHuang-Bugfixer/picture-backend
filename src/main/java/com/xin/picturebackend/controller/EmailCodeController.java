package com.xin.picturebackend.controller;

import com.xin.picturebackend.annotation.OnceTokenRequired;
import com.xin.picturebackend.common.BaseResponse;
import com.xin.picturebackend.common.ResultUtils;
import com.xin.picturebackend.constant.RedisKeyConstant;
import com.xin.picturebackend.service.EmailCodeService;
import com.xin.picturebackend.service.TokenService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.UUID;

/**
 *
 * @author 黄兴鑫
 * @since 2025/7/13 8:04
 */
@RestController
@RequestMapping("/email")
public class EmailCodeController {

    @Resource
    private EmailCodeService emailCodeService;

    @Resource
    private TokenService tokenService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("/sendCode")
    @OnceTokenRequired(needLogin = false)
    public BaseResponse<Boolean> sendEmailCode(@RequestParam("email") String email) {
        return ResultUtils.success(emailCodeService.sendCode(email));
    }

    @GetMapping("/sliderToken")
    public BaseResponse<Boolean> getSliderToken(HttpServletResponse response) {
        // fixme 可以直接请求该接口拿到 token，仍然存在脚本攻击问题。
        //  优化方案：滑块验证码通过后，请求携带滑块行为轨迹信息，后端分析该信息通过后才发放 token，而不是直接发送 token。
        String token = UUID.randomUUID().toString();
        String redisKey = RedisKeyConstant.ONCE_TOKEN_NOT_LOGIN_PREFIX + token;
        stringRedisTemplate.opsForValue().set(redisKey, token, Duration.ofMinutes(1));

        // 设置 cookie
        tokenService.writeTokenToCookies(response, token, "onceToken");
        return ResultUtils.success(true);
    }
}

