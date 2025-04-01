package com.xin.picturebackend.manager.websocket.disruptor;

import com.xin.picturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.xin.picturebackend.model.entity.User;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

/**
 * 定义图片编辑事件，用于 Disruptor
 *
 * @author 黄兴鑫
 * @since 2025/3/31 19:56
 */
@Data
public class PictureEditEvent {

    /**
     * 消息
     */
    private PictureEditRequestMessage pictureEditRequestMessage;

    /**
     * 当前用户的 session
     */
    private WebSocketSession session;

    /**
     * 当前用户
     */
    private User user;

    /**
     * 图片 id
     */
    private Long pictureId;

}

