package com.xin.picturebackend.controller;

import com.xin.picturebackend.common.BaseResponse;
import com.xin.picturebackend.common.ResultUtils;
import com.xin.picturebackend.service.EmailCodeService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

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

    @GetMapping("/sendCode/{email}")
    public BaseResponse<Boolean> sendEmailCode(@RequestParam("email") String email) {
        return ResultUtils.success(emailCodeService.sendCode(email));
    }
}

