package com.xin.picturebackend.service;

import com.xin.picturebackend.config.rabbitmq.MQConstants;
import com.xin.picturebackend.model.dto.sr.SrTaskCreateRequest;
import com.xin.picturebackend.model.entity.SrTask;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.enums.SrTaskStatusEnum;
import com.xin.picturebackend.model.messagequeue.sr.SrResultMessage;
import com.xin.picturebackend.model.messagequeue.sr.SrTaskMessage;
import com.xin.picturebackend.service.impl.SrTaskServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
class SrTaskServiceFlowTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private PictureService pictureService;

    @Mock
    private UserService userService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SrTaskResultService srTaskResultService;

    private SrTaskServiceImpl srTaskService;

    @BeforeEach
    void setUp() {
        srTaskService = spy(new SrTaskServiceImpl());
        ReflectionTestUtils.setField(srTaskService, "rabbitTemplate", rabbitTemplate);
        ReflectionTestUtils.setField(srTaskService, "pictureService", pictureService);
        ReflectionTestUtils.setField(srTaskService, "userService", userService);
        ReflectionTestUtils.setField(srTaskService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(srTaskService, "srTaskResultService", srTaskResultService);
        ReflectionTestUtils.setField(srTaskService, "cosClientHost", "https://resourses.collabimage.afishingcat.xin");
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldCreateTaskThenUpdateToSucceededWhenReceiveResultMessages() {
        SrTaskCreateRequest request = new SrTaskCreateRequest();
        request.setInputFileKey("input/2026/02/23/demo.png");
        request.setScale(4);
        request.setModelName("RealESRGAN_x4plus");
        request.setModelVersion("v1.0.0");

        User loginUser = new User();
        loginUser.setId(20001L);

        SrTask dbTask = new SrTask();
        dbTask.setId(100001L);
        dbTask.setStatus(SrTaskStatusEnum.QUEUED.getValue());
        dbTask.setProgress(0);
        dbTask.setAttempt(1);

        doAnswer(invocation -> {
            SrTask saving = invocation.getArgument(0);
            saving.setId(dbTask.getId());
            dbTask.setTaskNo(saving.getTaskNo());
            dbTask.setUserId(saving.getUserId());
            dbTask.setInputFileKey(saving.getInputFileKey());
            dbTask.setScale(saving.getScale());
            dbTask.setModelName(saving.getModelName());
            dbTask.setModelVersion(saving.getModelVersion());
            dbTask.setTraceId(saving.getTraceId());
            return true;
        }).when(srTaskService).save(any(SrTask.class));
        doReturn(dbTask).when(srTaskService).getById(dbTask.getId());
        doAnswer(invocation -> {
            SrTask update = invocation.getArgument(0);
            if (update.getStatus() != null) {
                dbTask.setStatus(update.getStatus());
            }
            if (update.getProgress() != null) {
                dbTask.setProgress(update.getProgress());
            }
            if (update.getOutputFileKey() != null) {
                dbTask.setOutputFileKey(update.getOutputFileKey());
            }
            if (update.getCostMs() != null) {
                dbTask.setCostMs(update.getCostMs());
            }
            if (update.getErrorCode() != null || SrTaskStatusEnum.SUCCEEDED.getValue().equals(update.getStatus())) {
                dbTask.setErrorCode(update.getErrorCode());
            }
            if (update.getErrorMsg() != null || SrTaskStatusEnum.SUCCEEDED.getValue().equals(update.getStatus())) {
                dbTask.setErrorMsg(update.getErrorMsg());
            }
            return true;
        }).when(srTaskService).updateById(any(SrTask.class));

        Long taskId = srTaskService.createTask(request, loginUser);
        assertEquals(dbTask.getId(), taskId);
        assertNotNull(dbTask.getTaskNo());
        assertEquals("input/2026/02/23/demo.png", dbTask.getInputFileKey());

        ArgumentCaptor<SrTaskMessage> taskMessageCaptor = ArgumentCaptor.forClass(SrTaskMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(MQConstants.SR_TASK_EXCHANGE),
                eq(MQConstants.SR_TASK_ROUTING_KEY),
                taskMessageCaptor.capture(),
                any(MessagePostProcessor.class)
        );
        SrTaskMessage sentMessage = taskMessageCaptor.getValue();
        assertEquals(taskId, sentMessage.getTaskId());
        assertEquals("image", sentMessage.getType());
        assertEquals("input/2026/02/23/demo.png", sentMessage.getInputFileKey());
        assertEquals("RealESRGAN_x4plus", sentMessage.getModelName());

        when(valueOperations.setIfAbsent(eq("sr:result:event:evt_result_running"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        SrResultMessage runningMessage = new SrResultMessage();
        runningMessage.setEventId("evt_result_running");
        runningMessage.setTaskId(taskId);
        runningMessage.setStatus(SrTaskStatusEnum.RUNNING.getValue());
        runningMessage.setProgress(20);
        srTaskService.handleResultMessage(runningMessage);
        assertEquals(SrTaskStatusEnum.RUNNING.getValue(), dbTask.getStatus());
        assertEquals(20, dbTask.getProgress());

        when(valueOperations.setIfAbsent(eq("sr:result:event:evt_result_success"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        SrResultMessage successMessage = new SrResultMessage();
        successMessage.setEventId("evt_result_success");
        successMessage.setTaskId(taskId);
        successMessage.setStatus(SrTaskStatusEnum.SUCCEEDED.getValue());
        successMessage.setProgress(100);
        successMessage.setOutputFileKey("output/100001/SR_x4.png");
        successMessage.setCostMs(1234L);
        srTaskService.handleResultMessage(successMessage);

        assertEquals(SrTaskStatusEnum.SUCCEEDED.getValue(), dbTask.getStatus());
        assertEquals(100, dbTask.getProgress());
        assertEquals("output/100001/SR_x4.png", dbTask.getOutputFileKey());
        assertEquals(1234L, dbTask.getCostMs());
    }

    @Test
    void shouldLockModelAndKeepVideoProcessingModeUnsetByDefault() {
        User loginUser = new User();
        loginUser.setId(20001L);

        SrTask explicitTask = buildSavedTask(200002L);
        doAnswer(invocation -> {
            SrTask saving = invocation.getArgument(0);
            copyTaskSnapshot(explicitTask, saving);
            return true;
        }).when(srTaskService).save(any(SrTask.class));

        SrTaskCreateRequest explicitRequest = new SrTaskCreateRequest();
        explicitRequest.setType("video");
        explicitRequest.setInputFileKey("input/video/2026/03/anime.mp4");
        explicitRequest.setScale(4);
        explicitRequest.setModelName("realesr-animevideov3");
        explicitRequest.setModelVersion("v1.0.0");

        Long explicitTaskId = srTaskService.createTask(explicitRequest, loginUser);
        assertEquals(explicitTask.getId(), explicitTaskId);
        assertEquals("RealESRGAN_x4plus", explicitTask.getModelName());
        assertNull(explicitTask.getVideoOptionsJson());

        ArgumentCaptor<SrTaskMessage> messageCaptor = ArgumentCaptor.forClass(SrTaskMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(MQConstants.SR_TASK_EXCHANGE),
                eq(MQConstants.SR_TASK_ROUTING_KEY),
                messageCaptor.capture(),
                any(MessagePostProcessor.class)
        );
        SrTaskMessage explicitMessage = messageCaptor.getValue();
        assertEquals("video", explicitMessage.getType());
        assertEquals("RealESRGAN_x4plus", explicitMessage.getModelName());
        assertNull(explicitMessage.getVideoOptions());
    }

    @Test
    void shouldPublishTaskMessageAfterCommit() {
        User loginUser = new User();
        loginUser.setId(20001L);

        SrTask savedTask = buildSavedTask(300001L);
        doAnswer(invocation -> {
            SrTask saving = invocation.getArgument(0);
            copyTaskSnapshot(savedTask, saving);
            return true;
        }).when(srTaskService).save(any(SrTask.class));

        SrTaskCreateRequest request = new SrTaskCreateRequest();
        request.setInputFileKey("input/2026/03/demo.png");
        request.setScale(4);

        TransactionSynchronizationManager.initSynchronization();
        Long taskId = srTaskService.createTask(request, loginUser);

        assertEquals(savedTask.getId(), taskId);
        verify(rabbitTemplate, never()).convertAndSend(
                eq(MQConstants.SR_TASK_EXCHANGE),
                eq(MQConstants.SR_TASK_ROUTING_KEY),
                any(SrTaskMessage.class),
                any(MessagePostProcessor.class)
        );

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertFalse(synchronizations.isEmpty());
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(MQConstants.SR_TASK_EXCHANGE),
                eq(MQConstants.SR_TASK_ROUTING_KEY),
                any(SrTaskMessage.class),
                any(MessagePostProcessor.class)
        );
    }

    private SrTask buildSavedTask(Long id) {
        SrTask task = new SrTask();
        task.setId(id);
        task.setStatus(SrTaskStatusEnum.QUEUED.getValue());
        task.setProgress(0);
        task.setAttempt(0);
        return task;
    }

    private void copyTaskSnapshot(SrTask target, SrTask saving) {
        saving.setId(target.getId());
        target.setTaskNo(saving.getTaskNo());
        target.setUserId(saving.getUserId());
        target.setBizType(saving.getBizType());
        target.setInputFileKey(saving.getInputFileKey());
        target.setScale(saving.getScale());
        target.setModelName(saving.getModelName());
        target.setModelVersion(saving.getModelVersion());
        target.setTraceId(saving.getTraceId());
        target.setVideoOptionsJson(saving.getVideoOptionsJson());
    }
}
