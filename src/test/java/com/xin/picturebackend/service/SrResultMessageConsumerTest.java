package com.xin.picturebackend.service;

import cn.hutool.json.JSONUtil;
import com.xin.picturebackend.model.messagequeue.sr.SrResultMessage;
import com.xin.picturebackend.service.messageconsumer.SrResultMessageConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SrResultMessageConsumerTest {

    @Mock
    private SrTaskService srTaskService;

    private SrResultMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SrResultMessageConsumer();
        ReflectionTestUtils.setField(consumer, "srTaskService", srTaskService);
    }

    @Test
    void shouldDeserializeAndForwardResultMessage() {
        SrResultMessage resultMessage = new SrResultMessage();
        resultMessage.setEventId("evt_result_1");
        resultMessage.setTaskId(1001L);
        resultMessage.setStatus("RUNNING");
        resultMessage.setProgress(10);

        Message message = MessageBuilder
                .withBody(JSONUtil.toJsonStr(resultMessage).getBytes(StandardCharsets.UTF_8))
                .build();
        consumer.onResult(message);

        ArgumentCaptor<SrResultMessage> captor = ArgumentCaptor.forClass(SrResultMessage.class);
        verify(srTaskService).handleResultMessage(captor.capture());
        assertEquals(1001L, captor.getValue().getTaskId());
        assertEquals("RUNNING", captor.getValue().getStatus());
        assertEquals(10, captor.getValue().getProgress());
    }
}

