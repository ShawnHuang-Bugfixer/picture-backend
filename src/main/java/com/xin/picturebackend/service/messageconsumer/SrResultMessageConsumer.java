package com.xin.picturebackend.service.messageconsumer;

import cn.hutool.json.JSONUtil;
import com.xin.picturebackend.model.messagequeue.sr.SrResultMessage;
import com.xin.picturebackend.service.SrTaskService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

/**
 * 超分结果消息消费者
 */
@Service
public class SrResultMessageConsumer {

    @Resource
    private SrTaskService srTaskService;

    @RabbitListener(queues = "#{@srResultQueue.name}")
    public void onResult(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        SrResultMessage resultMessage = JSONUtil.toBean(body, SrResultMessage.class);
        srTaskService.handleResultMessage(resultMessage);
    }
}
