package com.xin.picturebackend.service;

import com.xin.picturebackend.config.rabbitmq.MQConstants;
import com.xin.picturebackend.model.dto.sr.SrTaskCreateRequest;
import com.xin.picturebackend.model.entity.SrTask;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.enums.SrTaskStatusEnum;
import com.xin.picturebackend.model.messagequeue.sr.SrResultMessage;
import com.xin.picturebackend.model.messagequeue.sr.SrTaskMessage;
import com.xin.picturebackend.service.impl.SrTaskServiceImpl;
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

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
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

    private SrTaskServiceImpl srTaskService;

    @BeforeEach
    void setUp() {
        srTaskService = spy(new SrTaskServiceImpl());
        ReflectionTestUtils.setField(srTaskService, "rabbitTemplate", rabbitTemplate);
        ReflectionTestUtils.setField(srTaskService, "pictureService", pictureService);
        ReflectionTestUtils.setField(srTaskService, "userService", userService);
        ReflectionTestUtils.setField(srTaskService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(srTaskService, "cosClientHost", "https://resourses.collabimage.afishingcat.xin");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
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
            if (update.getErrorCode() != null || update.getStatus().equals(SrTaskStatusEnum.SUCCEEDED.getValue())) {
                dbTask.setErrorCode(update.getErrorCode());
            }
            if (update.getErrorMsg() != null || update.getStatus().equals(SrTaskStatusEnum.SUCCEEDED.getValue())) {
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
}

