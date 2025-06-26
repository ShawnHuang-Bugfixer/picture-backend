package com.xin.picturebackend.crontask;

import com.xin.picturebackend.manager.UserConnectionManager;
import com.xin.picturebackend.manager.connections.UserConnectionState;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 *
 * @author 黄兴鑫
 * @since 2025/6/26 15:51
 */
@Component
public class HeartbeatMonitor {

    @Resource
    private UserConnectionManager connectionManager;

    // 允许最大心跳间隔：60 秒
    private static final Duration MAX_INACTIVE_DURATION = Duration.ofSeconds(60);

    @Scheduled(fixedDelay = 30000) // 每 30 秒执行一次
    public void checkHeartbeat() {
        Instant now = Instant.now();

        for (Map.Entry<Long, UserConnectionState> entry : connectionManager.getAllConnections().entrySet()) {
            Long userId = entry.getKey();
            UserConnectionState state = entry.getValue();
            if (Duration.between(state.getLastActive(), now).compareTo(MAX_INACTIVE_DURATION) > 0) {
                // 超时，主动断开连接
                Object conn = state.getConnectionRef();
                if (conn instanceof WebSocketSession) {
                    try {
                        ((WebSocketSession) conn).close(CloseStatus.GOING_AWAY);
                    } catch (IOException e) {
                        // 忽略
                    }
                }
                connectionManager.unregister(userId);
            }
        }
    }
}

