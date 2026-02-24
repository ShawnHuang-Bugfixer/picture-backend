package com.xin.picturebackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xin.picturebackend.common.BaseResponse;
import com.xin.picturebackend.common.ResultUtils;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import com.xin.picturebackend.model.dto.sr.SrTaskCreateRequest;
import com.xin.picturebackend.model.dto.sr.SrTaskQueryRequest;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.vo.sr.SrTaskVO;
import com.xin.picturebackend.service.SrTaskService;
import com.xin.picturebackend.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 超分任务接口
 */
@RestController
@RequestMapping("/sr/task")
public class SrTaskController {

    @Resource
    private SrTaskService srTaskService;

    @Resource
    private UserService userService;

    @PostMapping("/create")
    public BaseResponse<Long> createTask(@RequestBody SrTaskCreateRequest createRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(createRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long taskId = srTaskService.createTask(createRequest, loginUser);
        return ResultUtils.success(taskId);
    }

    @GetMapping("/get/vo")
    public BaseResponse<SrTaskVO> getTaskVOById(Long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        SrTaskVO taskVO = srTaskService.getSrTaskVOById(id, loginUser);
        return ResultUtils.success(taskVO);
    }

    @PostMapping("/list/page/my/vo")
    public BaseResponse<Page<SrTaskVO>> listMyTaskByPage(@RequestBody SrTaskQueryRequest queryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Page<SrTaskVO> result = srTaskService.listMyTaskByPage(queryRequest, loginUser);
        return ResultUtils.success(result);
    }
}

