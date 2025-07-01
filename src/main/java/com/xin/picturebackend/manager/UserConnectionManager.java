package com.xin.picturebackend.manager;

import com.xin.picturebackend.manager.connections.ConnectionType;
import com.xin.picturebackend.manager.connections.UserConnectionState;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理用户和其对应的 websocket 或 SSE 连接。
 *
 * @author 黄兴鑫
 * @since 2025/6/26 15:23
 */
@Component
public class UserConnectionManager {
    private final ConcurrentHashMap<Long, UserConnectionState> connections = new ConcurrentHashMap<>();

    public synchronized boolean register(Long userId, ConnectionType type, Object connectionRef) {
        UserConnectionState existing = connections.get(userId);
        // 优先使用 WebSocket，如果已有 WebSocket 则忽略 SSE
        if (existing != null && existing.getType() == ConnectionType.WEBSOCKET && type == ConnectionType.SSE) {
            return false; // 拒绝降级注册
        }
        connections.put(userId, new UserConnectionState(userId, type, connectionRef, Instant.now()));
        return true;
    }

    // 断开连接
    public void unregister(Long userId) {
        connections.remove(userId);
    }

    // 判断是否在线
    public boolean isOnline(Long userId) {
        return connections.containsKey(userId);
    }

    // 获取连接类型
    public ConnectionType getConnectionType(Long userId) {
        return Optional.ofNullable(connections.get(userId)).map(UserConnectionState::getType).orElse(null);
    }

    // 获取连接对象（用于推送）
    public Object getConnectionRef(Long userId) {
        return Optional.ofNullable(connections.get(userId)).map(UserConnectionState::getConnectionRef).orElse(null);
    }

    public void updateHeartbeat(Long userId) {
        UserConnectionState state = connections.get(userId);
        if (state != null) {
            state.setLastActive(Instant.now());
        }
    }

    public ConcurrentHashMap<Long, UserConnectionState> getAllConnections() {
        return connections;
    }
}
