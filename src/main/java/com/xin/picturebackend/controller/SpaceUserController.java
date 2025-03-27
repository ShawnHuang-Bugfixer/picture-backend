package com.xin.picturebackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xin.picturebackend.common.BaseResponse;
import com.xin.picturebackend.common.DeleteRequest;
import com.xin.picturebackend.common.ResultUtils;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import com.xin.picturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.xin.picturebackend.model.dto.spaceuser.SpaceUserEditRequest;
import com.xin.picturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.xin.picturebackend.model.entity.SpaceUser;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.enums.RoleEnum;
import com.xin.picturebackend.model.vo.SpaceUserVO;
import com.xin.picturebackend.service.SpaceUserService;
import com.xin.picturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author 黄兴鑫
 * @since 2025/3/27 19:33
 */
@RestController
@RequestMapping("/spaceUser")
@Slf4j
public class SpaceUserController {
    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private UserService userService;

    @PostMapping("/add")
    public BaseResponse<Long> addSpaceUser(@RequestBody SpaceUserAddRequest spaceUserAddRequest) {
        if (spaceUserAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long result = spaceUserService.addSpaceUser(spaceUserAddRequest);
        return ResultUtils.success(result);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpaceUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long spaceUserId = deleteRequest.getId();
        SpaceUser dbSpaceUser = spaceUserService.getById(spaceUserId);
        ThrowUtils.throwIf(dbSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        if (dbSpaceUser.getSpaceRole().equals(RoleEnum.TEAM_SPACE_OWNER.getValue())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "不能删除空间管理员");
        }
        boolean result = spaceUserService.removeById(deleteRequest.getId());
        return ResultUtils.success(result);
    }

    @PostMapping("/get")
    public BaseResponse<SpaceUser> getSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest) {
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = spaceUserQueryRequest.getId();
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        String spaceRole = spaceUserQueryRequest.getSpaceRole();
        QueryWrapper<SpaceUser> queryWrapper = spaceUserService.getQueryWrapper(spaceUserQueryRequest);
        SpaceUser spaceUser = spaceUserService.getOne(queryWrapper);
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(spaceUser);
    }

    @PostMapping("/list")
    public BaseResponse<List<SpaceUserVO>> listSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest) {
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        QueryWrapper<SpaceUser> queryWrapper = spaceUserService.getQueryWrapper(spaceUserQueryRequest);
        List<SpaceUser> list = spaceUserService.list(queryWrapper);
        List<SpaceUserVO> spaceUserVOList = spaceUserService.getSpaceUserVOList(list);
        return ResultUtils.success(spaceUserVOList);
    }

    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpaceUser(@RequestBody SpaceUserEditRequest spaceUserEditRequest) {
        if (spaceUserEditRequest == null || spaceUserEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (spaceUserEditRequest.getSpaceRole().equals(RoleEnum.TEAM_SPACE_OWNER.getValue())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "空间管理员唯一");
        }
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserEditRequest, spaceUser);
        spaceUserService.validSpaceUser(spaceUser, false);
        long id = spaceUserEditRequest.getId();
        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = spaceUserService.updateById(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 查询我加入的团队空间列表
     */
    @PostMapping("/list/my")
    public BaseResponse<List<SpaceUserVO>> listMyTeamSpace(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        SpaceUserQueryRequest spaceUserQueryRequest = new SpaceUserQueryRequest();
        spaceUserQueryRequest.setUserId(loginUser.getId());
        List<SpaceUser> spaceUserList = spaceUserService.list(
                spaceUserService.getQueryWrapper(spaceUserQueryRequest)
        );
        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
    }
}
