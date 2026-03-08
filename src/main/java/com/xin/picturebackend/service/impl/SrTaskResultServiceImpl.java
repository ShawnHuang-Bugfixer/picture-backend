package com.xin.picturebackend.service.impl;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import com.xin.picturebackend.mapper.SrTaskResultMapper;
import com.xin.picturebackend.model.dto.sr.SrTaskResultQueryRequest;
import com.xin.picturebackend.model.dto.sr.SrTaskSpaceResultQueryRequest;
import com.xin.picturebackend.model.entity.Space;
import com.xin.picturebackend.model.entity.SpaceUser;
import com.xin.picturebackend.model.entity.SrTask;
import com.xin.picturebackend.model.entity.SrTaskResult;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.enums.SpaceTypeEnum;
import com.xin.picturebackend.model.messagequeue.sr.SrResultMessage;
import com.xin.picturebackend.model.vo.sr.SrTaskResultVO;
import com.xin.picturebackend.service.SpaceService;
import com.xin.picturebackend.service.SpaceUserService;
import com.xin.picturebackend.service.SrTaskResultService;
import com.xin.picturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 超分结果服务
 */
@Service
@Slf4j
public class SrTaskResultServiceImpl extends ServiceImpl<SrTaskResultMapper, SrTaskResult> implements SrTaskResultService {

    private static final Set<String> SUPPORTED_BIZ_TYPE = Set.of("image", "video");
    private static final Set<String> SUPPORTED_TEAM_ROLES = Set.of("viewer", "editor", "admin");

    @Value("${cos.client.host}")
    private String cosClientHost;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private UserService userService;

    @Override
    public Page<SrTaskResultVO> listMyResultByPage(SrTaskResultQueryRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null || loginUser == null, ErrorCode.PARAMS_ERROR);
        validatePageParams(request.getCurrent(), request.getPageSize(), request.getStartTime(), request.getEndTime());
        String bizType = StrUtil.isBlank(request.getBizType()) ? null : request.getBizType().toLowerCase(Locale.ROOT);
        if (StrUtil.isNotBlank(request.getBizType())) {
            ThrowUtils.throwIf(!SUPPORTED_BIZ_TYPE.contains(bizType), ErrorCode.PARAMS_ERROR, "bizType 仅支持 image 或 video");
        }

        QueryWrapper<SrTaskResult> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", loginUser.getId());
        queryWrapper.like(StrUtil.isNotBlank(request.getTaskNo()), "task_no", request.getTaskNo());
        queryWrapper.eq(StrUtil.isNotBlank(bizType), "biz_type", bizType);
        queryWrapper.eq(StrUtil.isNotBlank(request.getModelName()), "model_name", request.getModelName());
        queryWrapper.ge(request.getStartTime() != null, "created_at", request.getStartTime());
        queryWrapper.le(request.getEndTime() != null, "created_at", request.getEndTime());
        queryWrapper.orderByDesc("created_at");

        Page<SrTaskResult> taskResultPage = this.page(new Page<>(request.getCurrent(), request.getPageSize()), queryWrapper);
        Page<SrTaskResultVO> resultPage = new Page<>(taskResultPage.getCurrent(), taskResultPage.getSize(), taskResultPage.getTotal());
        List<SrTaskResultVO> records = taskResultPage.getRecords().stream().map(this::toVoWithOutputUrl).collect(Collectors.toList());
        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    public Page<SrTaskResultVO> listSpaceResultByPage(SrTaskSpaceResultQueryRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null || loginUser == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getSpaceId() == null || request.getSpaceId() <= 0, ErrorCode.PARAMS_ERROR, "spaceId 非法");
        validatePageParams(request.getCurrent(), request.getPageSize(), request.getStartTime(), request.getEndTime());
        checkTeamSpaceViewAuth(request.getSpaceId(), loginUser);
        String bizType = StrUtil.isBlank(request.getBizType()) ? null : request.getBizType().toLowerCase(Locale.ROOT);
        if (StrUtil.isNotBlank(request.getBizType())) {
            ThrowUtils.throwIf(!SUPPORTED_BIZ_TYPE.contains(bizType), ErrorCode.PARAMS_ERROR, "bizType 仅支持 image 或 video");
        }

        QueryWrapper<SrTaskResult> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("space_id", request.getSpaceId());
        queryWrapper.eq(StrUtil.isNotBlank(bizType), "biz_type", bizType);
        queryWrapper.like(StrUtil.isNotBlank(request.getTaskNo()), "task_no", request.getTaskNo());
        queryWrapper.eq(StrUtil.isNotBlank(request.getModelName()), "model_name", request.getModelName());
        queryWrapper.ge(request.getStartTime() != null, "created_at", request.getStartTime());
        queryWrapper.le(request.getEndTime() != null, "created_at", request.getEndTime());
        queryWrapper.orderByDesc("created_at");

        Page<SrTaskResult> taskResultPage = this.page(new Page<>(request.getCurrent(), request.getPageSize()), queryWrapper);
        Page<SrTaskResultVO> resultPage = new Page<>(taskResultPage.getCurrent(), taskResultPage.getSize(), taskResultPage.getTotal());
        List<SrTaskResultVO> records = taskResultPage.getRecords().stream().map(this::toVoWithOutputUrl).collect(Collectors.toList());
        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    public void saveOrUpdateSuccessResult(SrTask srTask, SrResultMessage resultMessage) {
        if (srTask == null || resultMessage == null || resultMessage.getTaskId() == null) {
            return;
        }
        String outputFileKey = resultMessage.getOutputFileKey();
        if (StrUtil.isBlank(outputFileKey)) {
            log.warn("超分成功结果缺少 outputFileKey, taskId={}", resultMessage.getTaskId());
            return;
        }
        SrTaskResult newResult = buildTaskResult(srTask, resultMessage);
        SrTaskResult existed = this.lambdaQuery()
                .eq(SrTaskResult::getTaskId, resultMessage.getTaskId())
                .one();
        if (existed == null) {
            try {
                this.save(newResult);
                return;
            } catch (DuplicateKeyException ignore) {
                // 竞争场景下回落到更新
            }
        }
        if (existed == null) {
            existed = this.lambdaQuery()
                    .eq(SrTaskResult::getTaskId, resultMessage.getTaskId())
                    .one();
        }
        if (existed == null) {
            return;
        }
        newResult.setId(existed.getId());
        this.updateById(newResult);
    }

    private SrTaskResult buildTaskResult(SrTask srTask, SrResultMessage resultMessage) {
        SrTaskResult result = new SrTaskResult();
        result.setTaskId(srTask.getId());
        result.setTaskNo(srTask.getTaskNo());
        result.setUserId(srTask.getUserId());
        result.setSpaceId(srTask.getSpaceId());
        result.setBizType(srTask.getBizType());
        result.setModelName(srTask.getModelName());
        result.setModelVersion(srTask.getModelVersion());
        result.setOutputFileKey(resultMessage.getOutputFileKey());
        result.setOutputFormat(resolveOutputFormat(resultMessage.getOutputFileKey()));
        result.setOutputSize(resultMessage.getOutputSize());
        result.setOutputWidth(resultMessage.getOutputWidth());
        result.setOutputHeight(resultMessage.getOutputHeight());
        result.setDurationMs(resultMessage.getDurationMs());
        result.setFps(resultMessage.getFps());
        result.setBitrateKbps(resultMessage.getBitrateKbps());
        result.setCodec(resultMessage.getCodec());
        result.setCostMs(ObjUtil.defaultIfNull(resultMessage.getCostMs(), 0L));
        result.setAttempt(ObjUtil.defaultIfNull(resultMessage.getAttempt(), 1));
        result.setTraceId(StrUtil.blankToDefault(resultMessage.getTraceId(), srTask.getTraceId()));
        result.setExtraJson(resultMessage.getExtraJson());
        return result;
    }

    private String resolveOutputFormat(String outputFileKey) {
        if (StrUtil.isBlank(outputFileKey)) {
            return null;
        }
        int queryIndex = outputFileKey.indexOf('?');
        String purePath = queryIndex >= 0 ? outputFileKey.substring(0, queryIndex) : outputFileKey;
        int dotIndex = purePath.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == purePath.length() - 1) {
            return null;
        }
        return purePath.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void validatePageParams(long current, long size, Date startTime, Date endTime) {
        ThrowUtils.throwIf(size > 50, ErrorCode.PARAMS_ERROR, "分页大小不能超过 50");
        ThrowUtils.throwIf(current <= 0, ErrorCode.PARAMS_ERROR, "页码必须大于 0");
        if (ObjUtil.isAllNotEmpty(startTime, endTime)) {
            ThrowUtils.throwIf(startTime.after(endTime), ErrorCode.PARAMS_ERROR, "开始时间不能晚于结束时间");
        }
    }

    private void checkTeamSpaceViewAuth(Long spaceId, User loginUser) {
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtils.throwIf(space.getSpaceType() == null || space.getSpaceType() != SpaceTypeEnum.TEAM.getValue(), ErrorCode.PARAMS_ERROR, "仅支持团队空间查询");
        if (userService.isAdmin(loginUser)) {
            return;
        }
        SpaceUser spaceUser = spaceUserService.lambdaQuery()
                .eq(SpaceUser::getSpaceId, spaceId)
                .eq(SpaceUser::getUserId, loginUser.getId())
                .one();
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NO_AUTH_ERROR, "无权限访问该团队空间");
        ThrowUtils.throwIf(!SUPPORTED_TEAM_ROLES.contains(spaceUser.getSpaceRole()), ErrorCode.NO_AUTH_ERROR, "团队角色无查看权限");
    }

    private SrTaskResultVO toVoWithOutputUrl(SrTaskResult taskResult) {
        SrTaskResultVO vo = SrTaskResultVO.objToVo(taskResult);
        if (vo == null || StrUtil.isBlank(vo.getOutputFileKey())) {
            return vo;
        }
        vo.setOutputUrl(buildCosUrl(vo.getOutputFileKey()));
        return vo;
    }

    private String buildCosUrl(String outputFileKey) {
        if (StrUtil.isBlank(outputFileKey)) {
            return null;
        }
        if (outputFileKey.startsWith("http://") || outputFileKey.startsWith("https://")) {
            return outputFileKey;
        }
        if (StrUtil.isBlank(cosClientHost)) {
            return outputFileKey;
        }
        String host = StrUtil.removeSuffix(cosClientHost, "/");
        String key = StrUtil.removePrefix(outputFileKey, "/");
        return host + "/" + key;
    }
}
