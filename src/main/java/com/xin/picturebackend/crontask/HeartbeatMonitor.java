package com.xin.picturebackend.crontask;

import com.xin.picturebackend.manager.UserConnectionManager;
import com.xin.picturebackend.manager.connections.UserConnectionState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PostConstruct;
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

    @Value("${app.heartbeat}")
    private long maxInactiveSeconds;

    private Duration maxInactiveDuration;

    @PostConstruct
    public void init() {
        this.maxInactiveDuration = Duration.ofSeconds(maxInactiveSeconds);
    }

    @Scheduled(fixedDelay = 30000)
    public void checkHeartbeat() {
        Instant now = Instant.now();

        for (Map.Entry<Long, UserConnectionState> entry : connectionManager.getAllConnections().entrySet()) {
            Long userId = entry.getKey();
            UserConnectionState state = entry.getValue();
            if (Duration.between(state.getLastActive(), now).compareTo(maxInactiveDuration) > 0) {
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


