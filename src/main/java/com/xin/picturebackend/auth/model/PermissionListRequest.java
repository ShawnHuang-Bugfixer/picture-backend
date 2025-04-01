package com.xin.picturebackend.auth.model;

import lombok.Data;
import lombok.NonNull;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 请求权限列表
 *
 * @author 黄兴鑫
 * @since 2025/4/1 10:41
 */
@Data
public class PermissionListRequest {
    private Long spaceId;
    private Long userId;
    private Long pictureId;
}
