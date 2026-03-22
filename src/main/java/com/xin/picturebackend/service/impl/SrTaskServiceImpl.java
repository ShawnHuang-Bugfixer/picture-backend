package com.xin.picturebackend.service.impl;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xin.picturebackend.config.rabbitmq.MQConstants;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import com.xin.picturebackend.mapper.SrTaskMapper;
import com.xin.picturebackend.model.dto.sr.SrTaskCreateRequest;
import com.xin.picturebackend.model.dto.sr.SrTaskQueryRequest;
import com.xin.picturebackend.model.entity.Picture;
import com.xin.picturebackend.model.entity.Space;
import com.xin.picturebackend.model.entity.SpaceUser;
import com.xin.picturebackend.model.entity.SrTask;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.enums.SrBizTypeEnum;
import com.xin.picturebackend.model.enums.SrTaskStatusEnum;
import com.xin.picturebackend.model.enums.SpaceTypeEnum;
import com.xin.picturebackend.model.messagequeue.sr.SrResultMessage;
import com.xin.picturebackend.model.messagequeue.sr.SrTaskMessage;
import com.xin.picturebackend.model.vo.sr.SrTaskVO;
import com.xin.picturebackend.service.PictureService;
import com.xin.picturebackend.service.SpaceService;
import com.xin.picturebackend.service.SpaceUserService;
import com.xin.picturebackend.service.SrTaskResultService;
import com.xin.picturebackend.service.SrTaskService;
import com.xin.picturebackend.service.UserService;
import com.xin.picturebackend.utils.COSKeyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 超分任务服务
 */
@Service
@Slf4j
public class SrTaskServiceImpl extends ServiceImpl<SrTaskMapper, SrTask> implements SrTaskService {

    private static final String LOCKED_MODEL_NAME = "RealESRGAN_x4plus";

    private static final Set<String> FINAL_STATUS = Set.of(
            SrTaskStatusEnum.SUCCEEDED.getValue(),
            SrTaskStatusEnum.FAILED.getValue(),
            SrTaskStatusEnum.CANCELLED.getValue()
    );

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private UserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SrTaskResultService srTaskResultService;

    @Value("${cos.client.host}")
    private String cosClientHost;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(SrTaskCreateRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null || loginUser == null, ErrorCode.PARAMS_ERROR);
        String bizType = resolveBizType(request.getType());
        Integer scale = ObjUtil.defaultIfNull(request.getScale(), 4);
        ThrowUtils.throwIf(scale != 2 && scale != 4, ErrorCode.PARAMS_ERROR, "scale 仅支持 2 或 4");
        String modelName = resolveModelName(request.getModelName());
        String modelVersion = StrUtil.blankToDefault(request.getModelVersion(), "v1.0.0");
        validateVideoOptions(bizType, request.getVideoOptions());
        SrTaskMessage.VideoOptions messageVideoOptions = buildMessageVideoOptions(bizType, request.getVideoOptions());

        TaskSourceInfo taskSourceInfo = resolveTaskSource(request, loginUser, bizType);
        String inputFileKey = taskSourceInfo.getInputFileKey();
        String taskNo = generateTaskNo();
        String traceId = "trace_" + UUID.fastUUID().toString(true);

        SrTask srTask = new SrTask();
        srTask.setTaskNo(taskNo);
        srTask.setUserId(loginUser.getId());
        srTask.setSpaceId(taskSourceInfo.getSpaceId());
        srTask.setBizType(bizType);
        srTask.setInputFileKey(inputFileKey);
        srTask.setStatus(SrTaskStatusEnum.QUEUED.getValue());
        srTask.setProgress(0);
        srTask.setScale(scale);
        srTask.setModelName(modelName);
        srTask.setModelVersion(modelVersion);
        srTask.setAttempt(0);
        srTask.setTraceId(traceId);
        if (messageVideoOptions != null) {
            srTask.setVideoOptionsJson(JSONUtil.toJsonStr(messageVideoOptions));
        }
        boolean saved = this.save(srTask);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "创建超分任务失败");

        SrTaskMessage taskMessage = new SrTaskMessage();
        taskMessage.setSchemaVersion("1.0");
        taskMessage.setEventId("evt_task_" + UUID.fastUUID().toString(true));
        taskMessage.setTimestamp(Instant.now().toString());
        taskMessage.setTaskId(srTask.getId());
        taskMessage.setTaskNo(taskNo);
        taskMessage.setUserId(loginUser.getId());
        taskMessage.setType(bizType);
        taskMessage.setInputFileKey(inputFileKey);
        taskMessage.setScale(scale);
        taskMessage.setModelName(modelName);
        taskMessage.setModelVersion(modelVersion);
        taskMessage.setAttempt(1);
        taskMessage.setTraceId(traceId);
        if (messageVideoOptions != null) {
            taskMessage.setVideoOptions(messageVideoOptions);
        }

        publishTaskMessageAfterCommit(srTask.getId(), taskMessage);
        return srTask.getId();
    }

    @Override
    public SrTaskVO getSrTaskVOById(Long id, User loginUser) {
        ThrowUtils.throwIf(id == null || id <= 0 || loginUser == null, ErrorCode.PARAMS_ERROR);
        SrTask srTask = this.getById(id);
        ThrowUtils.throwIf(srTask == null, ErrorCode.NOT_FOUND_ERROR);
        checkTaskOwner(srTask, loginUser);
        return SrTaskVO.objToVo(srTask);
    }

    @Override
    public Page<SrTaskVO> listMyTaskByPage(SrTaskQueryRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null || loginUser == null, ErrorCode.PARAMS_ERROR);
        long current = request.getCurrent();
        long size = request.getPageSize();
        ThrowUtils.throwIf(size > 50, ErrorCode.PARAMS_ERROR, "分页大小不能超过 50");
        ThrowUtils.throwIf(current <= 0, ErrorCode.PARAMS_ERROR, "页码必须大于 0");
        if (StrUtil.isNotBlank(request.getStatus())) {
            ThrowUtils.throwIf(SrTaskStatusEnum.getByValue(request.getStatus()) == null, ErrorCode.PARAMS_ERROR, "状态非法");
        }

        QueryWrapper<SrTask> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", loginUser.getId());
        queryWrapper.eq(StrUtil.isNotBlank(request.getStatus()), "status", request.getStatus());
        queryWrapper.eq(request.getId() != null, "id", request.getId());
        queryWrapper.like(StrUtil.isNotBlank(request.getTaskNo()), "task_no", request.getTaskNo());
        queryWrapper.orderByDesc("created_at");

        Page<SrTask> taskPage = this.page(new Page<>(current, size), queryWrapper);
        Page<SrTaskVO> resultPage = new Page<>(taskPage.getCurrent(), taskPage.getSize(), taskPage.getTotal());
        List<SrTaskVO> records = taskPage.getRecords().stream().map(SrTaskVO::objToVo).collect(Collectors.toList());
        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleResultMessage(SrResultMessage resultMessage) {
        if (resultMessage == null || resultMessage.getTaskId() == null || StrUtil.isBlank(resultMessage.getStatus())) {
            return;
        }
        if (StrUtil.isBlank(resultMessage.getEventId())) {
            log.warn("超分结果消息缺少 eventId, taskId={}", resultMessage.getTaskId());
            return;
        }
        String dedupeKey = "sr:result:event:" + resultMessage.getEventId();
        Boolean absent = stringRedisTemplate.opsForValue()
                .setIfAbsent(dedupeKey, "1", Duration.ofDays(3));
        if (Boolean.FALSE.equals(absent)) {
            return;
        }
        SrTask srTask = this.getById(resultMessage.getTaskId());
        if (srTask == null) {
            log.warn("超分任务不存在, taskId={}", resultMessage.getTaskId());
            return;
        }
        if (FINAL_STATUS.contains(srTask.getStatus())) {
            return;
        }

        String status = resultMessage.getStatus();
        SrTaskStatusEnum statusEnum = SrTaskStatusEnum.getByValue(status);
        if (statusEnum == null) {
            log.warn("未知超分状态, taskId={}, status={}", resultMessage.getTaskId(), status);
            return;
        }
        if (!isTransitionAllowed(srTask.getStatus(), status)) {
            log.warn("忽略非法状态流转, taskId={}, from={}, to={}", srTask.getId(), srTask.getStatus(), status);
            return;
        }

        SrTask updateTask = new SrTask();
        updateTask.setId(srTask.getId());
        updateTask.setStatus(status);
        updateTask.setProgress(resultMessage.getProgress());
        updateTask.setOutputFileKey(resultMessage.getOutputFileKey());
        updateTask.setCostMs(resultMessage.getCostMs());
        updateTask.setAttempt(resultMessage.getAttempt());
        updateTask.setErrorCode(resultMessage.getErrorCode());
        updateTask.setErrorMsg(resultMessage.getErrorMsg());
        if (StrUtil.isNotBlank(resultMessage.getTraceId())) {
            updateTask.setTraceId(resultMessage.getTraceId());
        }
        if (SrTaskStatusEnum.SUCCEEDED == statusEnum) {
            updateTask.setProgress(100);
            updateTask.setErrorCode(null);
            updateTask.setErrorMsg(null);
        }
        boolean updated = this.updateById(updateTask);
        if (!updated) {
            return;
        }
        if (SrTaskStatusEnum.SUCCEEDED == statusEnum) {
            srTaskResultService.saveOrUpdateSuccessResult(srTask, resultMessage);
        }
    }

    private boolean isTransitionAllowed(String currentStatus, String targetStatus) {
        if (StrUtil.isBlank(currentStatus) || StrUtil.isBlank(targetStatus)) {
            return false;
        }
        if (StrUtil.equals(currentStatus, targetStatus)) {
            return true;
        }
        if (StrUtil.equals(currentStatus, SrTaskStatusEnum.QUEUED.getValue())) {
            return StrUtil.equalsAny(targetStatus,
                    SrTaskStatusEnum.RUNNING.getValue(),
                    SrTaskStatusEnum.SUCCEEDED.getValue(),
                    SrTaskStatusEnum.FAILED.getValue(),
                    SrTaskStatusEnum.CANCELLED.getValue());
        }
        if (StrUtil.equals(currentStatus, SrTaskStatusEnum.RUNNING.getValue())) {
            return StrUtil.equalsAny(targetStatus,
                    SrTaskStatusEnum.SUCCEEDED.getValue(),
                    SrTaskStatusEnum.FAILED.getValue(),
                    SrTaskStatusEnum.CANCELLED.getValue());
        }
        return false;
    }

    private void checkTaskOwner(SrTask srTask, User loginUser) {
        boolean isOwner = srTask.getUserId().equals(loginUser.getId());
        if (!isOwner && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    private TaskSourceInfo resolveTaskSource(SrTaskCreateRequest request, User loginUser, String bizType) {
        if (SrBizTypeEnum.VIDEO.getValue().equals(bizType)) {
            if (StrUtil.isNotBlank(request.getInputFileKey())) {
                return new TaskSourceInfo(request.getInputFileKey(), null);
            }
            Long pictureId = request.getPictureId();
            ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR, "video 任务必须传 inputFileKey 或 pictureId");
            Picture picture = pictureService.getById(pictureId);
            ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "资源不存在");
            checkPictureAccess(picture, loginUser);
            ThrowUtils.throwIf(StrUtil.isBlank(picture.getUrl()), ErrorCode.PARAMS_ERROR, "资源 url 为空");
            String inputFileKey = COSKeyUtils.cosKeyHandler(picture.getUrl(), cosClientHost);
            return new TaskSourceInfo(inputFileKey, picture.getSpaceId());
        }
        if (StrUtil.isNotBlank(request.getInputFileKey())) {
            return new TaskSourceInfo(request.getInputFileKey(), null);
        }
        Long pictureId = request.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR, "pictureId 和 inputFileKey 不能同时为空");
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        checkPictureAccess(picture, loginUser);
        ThrowUtils.throwIf(StrUtil.isBlank(picture.getUrl()), ErrorCode.PARAMS_ERROR, "图片 url 为空");
        String inputFileKey = COSKeyUtils.cosKeyHandler(picture.getUrl(), cosClientHost);
        return new TaskSourceInfo(inputFileKey, picture.getSpaceId());
    }

    private void checkPictureAccess(Picture picture, User loginUser) {
        if (picture.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)) {
            return;
        }
        Long spaceId = picture.getSpaceId();
        ThrowUtils.throwIf(spaceId == null, ErrorCode.NO_AUTH_ERROR);
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtils.throwIf(space.getSpaceType() == null || space.getSpaceType() != SpaceTypeEnum.TEAM.getValue(), ErrorCode.NO_AUTH_ERROR);
        SpaceUser spaceUser = spaceUserService.lambdaQuery()
                .eq(SpaceUser::getSpaceId, spaceId)
                .eq(SpaceUser::getUserId, loginUser.getId())
                .one();
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NO_AUTH_ERROR);
    }

    private String resolveBizType(String type) {
        if (StrUtil.isBlank(type)) {
            return SrBizTypeEnum.IMAGE.getValue();
        }
        SrBizTypeEnum bizTypeEnum = SrBizTypeEnum.getByValue(type);
        ThrowUtils.throwIf(bizTypeEnum == null, ErrorCode.PARAMS_ERROR, "type 仅支持 image 或 video");
        return bizTypeEnum.getValue();
    }

    private void validateVideoOptions(String bizType, SrTaskCreateRequest.VideoOptions videoOptions) {
        if (!SrBizTypeEnum.VIDEO.getValue().equals(bizType) || videoOptions == null) {
            return;
        }
        Double fpsOverride = videoOptions.getFpsOverride();
        ThrowUtils.throwIf(fpsOverride != null && fpsOverride <= 0, ErrorCode.PARAMS_ERROR, "fpsOverride 必须 > 0");
    }

    private SrTaskMessage.VideoOptions buildMessageVideoOptions(String bizType, SrTaskCreateRequest.VideoOptions requestVideoOptions) {
        if (!SrBizTypeEnum.VIDEO.getValue().equals(bizType) || requestVideoOptions == null) {
            return null;
        }
        SrTaskMessage.VideoOptions videoOptions = new SrTaskMessage.VideoOptions();
        videoOptions.setKeepAudio(ObjUtil.defaultIfNull(requestVideoOptions.getKeepAudio(), true));
        videoOptions.setExtractFrameFirst(requestVideoOptions.getExtractFrameFirst());
        videoOptions.setFpsOverride(requestVideoOptions.getFpsOverride());
        return videoOptions;
    }

    private String resolveModelName(String requestedModelName) {
        if (StrUtil.isBlank(requestedModelName)) {
            return LOCKED_MODEL_NAME;
        }
        if (!StrUtil.equals(requestedModelName, LOCKED_MODEL_NAME)) {
            log.warn("超分服务当前固定使用模型 {}, 忽略请求模型 {}", LOCKED_MODEL_NAME, requestedModelName);
        }
        return LOCKED_MODEL_NAME;
    }

    private void publishTaskMessageAfterCommit(Long taskId, SrTaskMessage taskMessage) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            doPublishTaskMessage(taskId, taskMessage);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doPublishTaskMessage(taskId, taskMessage);
            }
        });
    }

    private void doPublishTaskMessage(Long taskId, SrTaskMessage taskMessage) {
        try {
            rabbitTemplate.convertAndSend(
                    MQConstants.SR_TASK_EXCHANGE,
                    MQConstants.SR_TASK_ROUTING_KEY,
                    taskMessage,
                    message -> {
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    }
            );
        } catch (Exception e) {
            log.error("发送超分任务消息失败, taskId={}", taskId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "发送超分任务消息失败");
        }
    }

    private String generateTaskNo() {
        String now = DateUtil.format(new Date(), DatePattern.PURE_DATETIME_MS_PATTERN);
        return "SR" + now + RandomUtil.randomNumbers(4);
    }

    private static class TaskSourceInfo {
        private final String inputFileKey;
        private final Long spaceId;

        private TaskSourceInfo(String inputFileKey, Long spaceId) {
            this.inputFileKey = inputFileKey;
            this.spaceId = spaceId;
        }

        public String getInputFileKey() {
            return inputFileKey;
        }

        public Long getSpaceId() {
            return spaceId;
        }
    }
}
