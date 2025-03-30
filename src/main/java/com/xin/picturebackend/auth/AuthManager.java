package com.xin.picturebackend.auth;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xin.picturebackend.auth.enums.RoleEnum;
import com.xin.picturebackend.model.entity.Space;
import com.xin.picturebackend.model.entity.SpaceUser;
import com.xin.picturebackend.model.entity.User;
import com.xin.picturebackend.model.enums.SpaceTypeEnum;
import com.xin.picturebackend.service.SpaceUserService;
import com.xin.picturebackend.service.UserService;
import lombok.Data;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

@Component
public class AuthManager {
    private static final String CONFIG_PATH = "business/rolePermission.json";
    private static final RolePermissionMapping rolePermissionMapping;
    @Resource
    private UserService userService;
    @Resource
    private SpaceUserService spaceUserService;

    static {
        try {
            // 使用Hutool的ResourceUtil直接读取resources下的文件
            String jsonStr = ResourceUtil.readUtf8Str(CONFIG_PATH);

            // 使用Hutool解析JSON
            JSONObject jsonObject = JSONUtil.parseObj(jsonStr);

            // 转换为RolePermissionMapping对象
            rolePermissionMapping = new RolePermissionMapping();
            Map<String, RolePermissionMapping.RolePermission> mappings = new HashMap<>();

            jsonObject.forEach((roleKey, value) -> {
                JSONObject roleJson = (JSONObject) value;
                String roleName = roleJson.getStr("roleName");
                List<String> permissions = roleJson.getJSONArray("permissions").toList(String.class);

                mappings.put(roleKey, new RolePermissionMapping.RolePermission(roleName, permissions));
            });

            rolePermissionMapping.setMappings(mappings);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load role permissions from " + CONFIG_PATH, e);
        }
    }

    public static List<String> getPermissionsByRole(String roleName) {
        return rolePermissionMapping.mappings.get(roleName).getPermissions();
    }

    // 角色权限适配器，必须根据前端重写,仅仅为了复用教程逻
    public List<String> permissionsAdapter(List<String> paraPermissions, Space space, User loginUser) {
        // 管理员权限
        String[] permissions = new String[]{"spaceUser:manage",
                "picture:view",
                "picture:upload",
                "picture:edit",
                "picture:delete"};
        List<String> ADMIN_PERMISSIONS = Arrays.stream(permissions).toList();
        // 公共图库
        if (space == null) {
            if (userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            }
            return new ArrayList<>();
        }
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        if (spaceTypeEnum == null) {
            return new ArrayList<>();
        }
        // 根据空间获取对应的权限
        switch (spaceTypeEnum) {
            case PRIVATE:
                // 私有空间，仅本人或管理员有所有权限
                if (space.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    return new ArrayList<>();
                }
            case TEAM:
                // 团队空间，查询 SpaceUser 并获取角色和权限
                SpaceUser spaceUser = spaceUserService.lambdaQuery()
                        .eq(SpaceUser::getSpaceId, space.getId())
                        .eq(SpaceUser::getUserId, loginUser.getId())
                        .one();
                if (spaceUser == null) {
                    return new ArrayList<>();
                } else {
                    ArrayList<String> permissionList = new ArrayList<>();
                    String spaceRole = spaceUser.getSpaceRole();
                    switch (spaceRole) {
                        case "admin":
                            permissionList.addAll(Arrays.stream(permissions).toList());
                        case "viewer":
                            permissionList.add("picture:view");
                        case "editor":
                            permissionList.add("picture:view");
                            permissionList.add("picture:upload");
                            permissionList.add("picture:edit");
                            permissionList.add("picture:delete");
                    }
                    return permissionList;
                }
        }
        return new ArrayList<>();
    }

    @Data
    private static class RolePermissionMapping {
        private Map<String, RolePermission> mappings = new HashMap<>();

        @Data
        public static class RolePermission {
            private String roleName;
            private List<String> permissions;

            public RolePermission() {
                // 默认构造函数用于反序列化
            }

            public RolePermission(String roleName, List<String> permissions) {
                this.roleName = roleName;
                this.permissions = permissions;
            }
        }
    }

    public static void main(String[] args) {
        List<List<String>> list = RoleEnum.getAllValues().stream()
                .map(AuthManager::getPermissionsByRole)
                .toList();
        int i = 0;
        for (String allValue : RoleEnum.getAllValues()) {
            System.out.println(allValue);
            System.out.println(list.get(i++));
        }
    }
}