package com.xin.picturebackend.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xin.picturebackend.model.entity.Picture;
import com.xin.picturebackend.model.entity.Space;
import com.xin.picturebackend.model.entity.SpaceUser;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.auth.enums.RoleEnum;
import com.xin.picturebackend.service.PictureService;
import com.xin.picturebackend.service.SpaceService;
import com.xin.picturebackend.service.SpaceUserService;
import com.xin.picturebackend.service.UserService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author 黄兴鑫
 * @since 2025/3/29 13:51
 */
@Slf4j
@Component    // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展
public class StpInterfaceImpl implements StpInterface {
    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Resource
    @Lazy
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;

    /**
     * 返回一个账号所拥有的权限码集合
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        UserAuthContext userAuthContext = getUserAuthContextByCurrentRequest();
        Long userId = Long.valueOf(loginId.toString());
        Long pictureId = userAuthContext.getPictureId();
        Long spaceId = userAuthContext.getSpaceId();
        Long spaceUserId = userAuthContext.getSpaceUserId();
        return getPermissions(spaceId, userId, pictureId, spaceUserId);
    }

    public void addPermissionsBySpaceUser(ArrayList<String> permissions, SpaceUser dbSpaceuser) {
        if (ObjUtil.isNotNull(dbSpaceuser)) {
            switch (dbSpaceuser.getSpaceRole()) {
                case "admin":
                    log.error("团队空间拥有者" + "空间id：{}", dbSpaceuser.getSpaceId());
                    permissions.addAll(AuthManager.getPermissionsByRole(RoleEnum.TEAM_SPACE_OWNER.getValue()));
                    break;
                case "editor":
                    log.error("团队空间编辑者" + "空间id：{}", dbSpaceuser.getSpaceId());
                    permissions.addAll(AuthManager.getPermissionsByRole(RoleEnum.TEAM_SPACE_EDITOR.getValue()));
                    break;
                case "viewer":
                    log.error("团队空间浏览者" + "空间id：{}", dbSpaceuser.getSpaceId());
                    permissions.addAll(AuthManager.getPermissionsByRole(RoleEnum.TEAM_SPACE_VIEWER.getValue()));
                    break;
                default:
                    break;
            }
        }
    }

    public List<String> getPermissions(Long spaceId, @NonNull Long userId, Long pictureId, Long spaceUserId) {
        ArrayList<String> permissions = new ArrayList<>();
        // 1. userId 在 User 表中 userRole 为 admin，系统管理员
        User dbUser = userService.getById(userId);
        if (dbUser.getUserRole().equals(RoleEnum.SYSTEM_ADMIN.getValue())) {
            log.error("系统管理员：{}", userId);
            permissions.addAll(AuthManager.getPermissionsByRole(RoleEnum.SYSTEM_ADMIN.getValue()));
        }
        // 2. pictureId 在 picture 表中有记录
        if (ObjUtil.isNotNull(pictureId)) {
            Picture dbPicture = pictureService.getById(pictureId);
            if (ObjUtil.isNotNull(dbPicture)) {
                Long dbPictureSpaceId = dbPicture.getSpaceId();
                if (ObjUtil.isNull(dbPictureSpaceId) && dbPicture.getUserId().equals(userId)) {
                    // 2.1 如果图片属于公有空间，即 spaceId == null，且 pictureId 属于 userId，公共图片拥有者
                    permissions.addAll(AuthManager.getPermissionsByRole(RoleEnum.PUBLIC_SPACE_PICTURE_OWNER.getValue()));
                } else {
                    // 2.2 如果图片不属于公有空间，即 spaceId ！= null
                    Space dbSpace = spaceService.getById(dbPictureSpaceId);
                    if (ObjUtil.isNotNull(dbSpace)) {
                        Integer spaceType = dbSpace.getSpaceType();
                        if (spaceType.equals(0)) {
                            // 2.2.1 空间类型为私人空间
                            //       spaceId 属于 userId，私人空间拥有者
                            if (dbSpace.getUserId().equals(userId)) {
                                log.error("私人空间拥有者" + "空间id: {}", dbPictureSpaceId);
                                permissions.addAll(AuthManager.getPermissionsByRole(RoleEnum.PRIVATE_SPACE_OWNER.getValue()));
                            }
                        }
                        if (spaceType.equals(1)) {
                            // 2.2.2 空间类型为团队空间
                            //      查询 space_user 表，根据 spaceId 和 userId 查询 spaceRole 有记录，赋予对应角色
                            QueryWrapper<SpaceUser> spaceUserQueryWrapper = new QueryWrapper<>();
                            spaceUserQueryWrapper.select("spaceRole")
                                    .eq("spaceId", dbPictureSpaceId)
                                    .eq("userId", userId);
                            SpaceUser dbSpaceUser = spaceUserService.getOne(spaceUserQueryWrapper);
                            if (ObjUtil.isNotNull(dbSpaceUser)) {
                                addPermissionsBySpaceUser(permissions, dbSpaceUser);
                            }
                        }
                    }
                }
            }
        }
        // 3. spaceId 在 space 表中有记录
        if (ObjUtil.isNotNull(spaceId)) {
            Space dbSpace = spaceService.getById(spaceId);
            if (ObjUtil.isNotNull(dbSpace)) {
                // 3.1 spaceId 对应的空间类型 spaceType == 0 为私人空间
                // 3.1.1 spaceId 属于 userId，私人空间拥有者
                if (dbSpace.getSpaceType().equals(0) && dbSpace.getUserId().equals(userId)) {
                    log.error("私人空间拥有者" + "空间id: {}", spaceId);
                    permissions.addAll(AuthManager.getPermissionsByRole(RoleEnum.PRIVATE_SPACE_OWNER.getValue()));
                }
                // 3.2 spaceId 对应的空间类型 spaceType == 1 为团队空间
                // 3.2.1 根据 spaceId 和 userId 查询 space_user 表 spaceRole 有记录
                //       * spaceRole == admin 团队空间拥有者
                //       * spaceRole == viewer 团队空间浏览者
                //       * spaceRole == editor 团队空间编辑者
                if (dbSpace.getSpaceType().equals(1)) {
                    QueryWrapper<SpaceUser> spaceUserQueryWrapper = new QueryWrapper<>();
                    spaceUserQueryWrapper.select("spaceRole")
                            .eq("spaceId", spaceId)
                            .eq("userId", userId);
                    SpaceUser dbSpaceuser = spaceUserService.getOne(spaceUserQueryWrapper);
                    addPermissionsBySpaceUser(permissions, dbSpaceuser);
                }
            }
        }
        // 4. spaceUserid 在 space_user 表中有记录
        //       * spaceRole == admin 团队空间拥有者
        //       * spaceRole == viewer 团队空间浏览者
        //       * spaceRole == editor 团队空间编辑者
        if (ObjUtil.isNotNull(spaceUserId)) {
            SpaceUser dbSpaceuser = spaceUserService.getById(spaceUserId);
            // fixme 根据 spaceUserId 查询 spaceId, 根据 spaceId 和 userId 查询 spac_user 表，获取 userid 的权限
            Long teamSpaceId = dbSpaceuser.getSpaceId();
            QueryWrapper<SpaceUser> spaceUserQueryWrapper = new QueryWrapper<>();
            spaceUserQueryWrapper.select("spaceRole")
                    .eq("spaceId", teamSpaceId)
                    .eq("userId", userId);
            dbSpaceuser = spaceUserService.getOne(spaceUserQueryWrapper);
            addPermissionsBySpaceUser(permissions, dbSpaceuser);
        }
        permissions.addAll(AuthManager.getPermissionsByRole(RoleEnum.PUBLIC_SPACE_OWNER.getValue()));
        log.error("权限列表：{}", permissions);
        return permissions;
    }


    /**
     * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 本 list 仅做模拟，实际项目中要根据具体业务逻辑来查询角色
        List<String> list = new ArrayList<String>();
        list.add("admin");
        list.add("super-admin");
        return list;
    }

    private UserAuthContext getUserAuthContextByCurrentRequest() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String contentType = request.getContentType();
        UserAuthContext userAuthContext;
        // 处理 get 请求和 post 请求
        if (ContentType.JSON.getValue().equals(contentType)) { // 处理 post 请求
            String body = ServletUtil.getBody(request);
            userAuthContext = JSONUtil.toBean(body, UserAuthContext.class);
        } else {
            // 处理 get 请求
            Map<String, String> paramMap = ServletUtil.getParamMap(request);
            userAuthContext = BeanUtil.toBean(paramMap, UserAuthContext.class);
        }
        Long id = userAuthContext.getId();
        if (ObjUtil.isNotNull(id)) {
            // uri: /api/picture/**
            String requestUri = request.getRequestURI();
            String partUri = requestUri.replace(contextPath + "/", "");
            String moduleName = StrUtil.subBefore(partUri, "/", false);
            switch (moduleName) {
                case "picture":
                    userAuthContext.setPictureId(id);
                    break;
                case "spaceUser":
                    userAuthContext.setSpaceUserId(id);
                    break;
                case "space":
                    userAuthContext.setSpaceId(id);
                    break;
                default:
            }
        }
        return userAuthContext;
    }
}
