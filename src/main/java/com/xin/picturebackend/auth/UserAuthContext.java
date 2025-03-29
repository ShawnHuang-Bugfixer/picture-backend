package com.xin.picturebackend.auth;

import lombok.Data;

/**
 * 用户权限认证上下文
 *
 * @author 黄兴鑫
 * @since 2025/3/29 14:27
 */
@Data
public class UserAuthContext {
    /**
     * 临时参数，不同请求对应的 id 可能不同
     */
    private Long id;

    /**
     * 图片 ID
     */
    private Long pictureId;

    /**
     * 空间 ID
     */
    private Long spaceId;

    /**
     * 空间用户 ID
     */
    private Long spaceUserId;
}
