package com.xin.picturebackend.auth;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xin.picturebackend.service.SpaceUserService;
import com.xin.picturebackend.service.UserService;
import lombok.Data;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
}