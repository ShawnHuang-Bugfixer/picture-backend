package com.xin.picturebackend.manager.websocket.handler;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.manager.websocket.disruptor.PictureEditEventProducer;
import com.xin.picturebackend.manager.websocket.model.PictureEditActionEnum;
import com.xin.picturebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.xin.picturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.xin.picturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.service.UserService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定义编辑图片的 WebSocket 处理器，处理连接、断开连接、接收消息等事件。
 * fixme
 *      1. 服务端接收消息请求后直接将处理，并未持久化存储，而是直接广播给所有用户。导致新加入的用户无法看到其他用户编辑后的图片。
 *      2. 用户建立 websocket 后假如直接 logout，此时 websocket 连接未断开。
 *      3. 并未实现真正的协作功能，而是通过业务设计仅仅允许一个用户处于编辑状态。
 *
 * @author 黄兴鑫
 * @since 2025/3/31 17:08
 */
@Component
@Slf4j
public class PictureEditHandler extends TextWebSocketHandler {
    // key : pictureId, value : userId 确保仅有一个用户处于编辑状态。
    private final Map<Long, Long> editingPictureUserMap = new ConcurrentHashMap<>();

    // key : pictureId, value : WebSocketSession 某图片编辑时用户会话集合
    private final Map<Long, Set<WebSocketSession>> editPicUserSessionMap = new ConcurrentHashMap<>();

    @Resource
    private UserService userService;

    @Resource
    private PictureEditEventProducer pictureEditEventProducer;

    /**
     * 处理客户端连接，并广播给所有用户。
     *
     * @param session 用户 session
     */
    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        log.debug("用户 {} 连接成功", session.getId());
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        Long userId = (Long) session.getAttributes().get("userId");
        // pictureId 和 session 加入 editPicUserSessionMap
        editPicUserSessionMap.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        editPicUserSessionMap.get(pictureId).add(session);
        // 构造用户加入编辑信息并广播
        PictureEditResponseMessage responseMessage = new PictureEditResponseMessage();
        responseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        responseMessage.setMessage(String.format("%s 加入编辑空间", user.getUserName()));
        responseMessage.setUser(userService.getUserVO(user));
        broadcast(pictureId, responseMessage);
    }

    /**
     * 处理客户端消息，并广播给其他用户。
     *
     * @param session 用户 session
     * @param message 用户消息
     */
    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        PictureEditRequestMessage requestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        String type = requestMessage.getType();
        PictureEditMessageTypeEnum enumByValue = PictureEditMessageTypeEnum.getEnumByValue(type);
        // 从 Session 属性中获取公共参数
        Map<String, Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("pictureId");
        log.debug("用户 {} 发送 {} 消息", session.getId(), enumByValue.getText());
        pictureEditEventProducer.publishEvent(requestMessage, session, user, pictureId);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        Long pictureId = (Long) attributes.get("pictureId");
        User user = (User) attributes.get("user");
        // 移除当前用户的编辑状态
        handleExitEditMessage(null, session, user, pictureId);

        // 删除会话
        Set<WebSocketSession> sessionSet = editPicUserSessionMap.get(pictureId);
        if (sessionSet != null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                editPicUserSessionMap.remove(pictureId);
            }
        }

        // 响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        broadcast(pictureId, pictureEditResponseMessage);
    }

    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = editingPictureUserMap.get(pictureId);
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 移除当前用户的编辑状态
            editingPictureUserMap.remove(pictureId);
            // 构造响应，发送退出编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s退出编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            broadcast(pictureId, pictureEditResponseMessage);
        }
    }


    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = editingPictureUserMap.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            return;
        }
        // 确认是当前编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s执行%s", user.getUserName(), actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            // 广播给除了当前客户端之外的其他用户，否则会造成重复编辑
            broadcast(pictureId, pictureEditResponseMessage, session);
        }
    }

    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        // 没有用户正在编辑该图片，才能进入编辑
        if (!editingPictureUserMap.containsKey(pictureId)) {
            // 设置当前用户为编辑用户
            editingPictureUserMap.put(pictureId, user.getId());
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("%s开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            broadcast(pictureId, pictureEditResponseMessage);
        }
    }

    /**
     * 广播消息给排除指定用户后的其余用户。
     */
    private void broadcast(Long pictureId, PictureEditResponseMessage paraMessage, WebSocketSession excludeSession) {
        Set<WebSocketSession> userSessions = editPicUserSessionMap.get(pictureId);
        if (ObjectUtil.isNull(userSessions)) return;
        for (WebSocketSession userSession : userSessions) {
            if (ObjectUtil.equals(userSession, excludeSession)) continue;
            try {
                // 将消息序列化为 json 串。注意处理 Long 类型。
                ObjectMapper objectMapper = getObjectMapper();
                String messageJson = objectMapper.writeValueAsString(paraMessage);
                if (userSession.isOpen()) userSession.sendMessage(new TextMessage(messageJson));
            } catch (Exception e) {
                log.error("broadcast error", e);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "广播失败");
            }
        }
    }

    /**
     * 广播消息给所有用户。
     */
    private void broadcast(Long pictureId, PictureEditResponseMessage message) {
        broadcast(pictureId, message, null);
    }

    /**
     * 获取解决 Long 精度转化的 ObjectMapper 对象，用于序列化消息。
     */
    private ObjectMapper getObjectMapper() {
        // 创建 ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        // 配置序列化：将 Long 类型转为 String，解决丢失精度问题
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance); // 支持 long 基本类型
        objectMapper.registerModule(module);
        return objectMapper;
    }
}
