package com.xin.picturebackend.manager.connections;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 定义在消息推送模块中，用户连接状态。
 *
 * @author 黄兴鑫
 * @since 2025/6/26 15:19
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserConnectionState {
    private Long userId;
    private ConnectionType type;
    private Object connectionRef;
    private Instant lastActive;
}
