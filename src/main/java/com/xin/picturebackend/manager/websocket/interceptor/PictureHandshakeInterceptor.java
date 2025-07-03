package com.xin.picturebackend.manager.websocket.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.xin.picturebackend.auth.constant.PermissionConstants;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.model.entity.Picture;
import com.xin.picturebackend.model.entity.Space;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.enums.SpaceTypeEnum;
import com.xin.picturebackend.service.PictureService;
import com.xin.picturebackend.service.SpaceService;
import com.xin.picturebackend.service.UserService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * webSocket 拦截器，用于拦截连接请求校验权限。要求登录且具备当前团队空间的修改图片权限
 *
 * @author 黄兴鑫
 * @since 2025/3/31 16:46
 */
@Slf4j
@Component
public class PictureHandshakeInterceptor implements HandshakeInterceptor {
    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response, @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            // 获取 httpServletRequest
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            // 参数校验
            String pictureId = servletRequest.getParameter("pictureId");
            if (StrUtil.isBlank(pictureId)) {
                log.error("缺少图片参数，拒绝握手");
                return false;
            }
            // 连接校验
            boolean result = hasPermissionThenInitial(servletRequest, pictureId, attributes);
            if (!result) throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户没有团队空间修改图片权限，拒绝握手");
            return true;
        }
        return false;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response, @NonNull WebSocketHandler wsHandler, Exception exception) {
        log.error("握手结束");
    }

    private boolean hasPermissionThenInitial(HttpServletRequest servletRequest, String pictureId, Map<String, Object> attributes) {
        User loginUser = userService.getLoginUser(servletRequest);
        if (ObjUtil.isEmpty(loginUser)) {
            log.error("用户未登录，拒绝握手");
            return false;
        }
        // 权限校验
        Picture dbPicture = pictureService.getById(pictureId);
        if (ObjUtil.isNull(dbPicture)) {
            log.error("图片不存在，拒绝握手");
            return false;
        }
        if (ObjUtil.isNull(dbPicture.getSpaceId())) {
            log.error("图片不属于任何空间，拒绝握手");
            return false;
        }
        Space dbSpace = spaceService.getById(dbPicture.getSpaceId());
        if (ObjUtil.isNull(dbSpace)) {
            log.error("空间不存在，拒绝握手");
            return false;
        }
        if (dbSpace.getSpaceType().equals(SpaceTypeEnum.PRIVATE.getValue())) {
            log.error("私人空间不支持协作编辑");
            return false;
        }
        if (dbSpace.getSpaceType().equals(SpaceTypeEnum.TEAM.getValue())) {
            boolean hasPermission = StpUtil.hasPermission(PermissionConstants.TEAM_MODIFY_IMAGE);
            if (!hasPermission) {
                log.error("用户没有团队空间修改图片权限，拒绝握手");
                return false;
            }
        }
        attributes.put("user", loginUser);
        attributes.put("pictureId", Long.parseLong(pictureId));
        attributes.put("userId", loginUser.getId());
        log.error("握手成功！");
        return true;
    }
}
