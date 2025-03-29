package com.xin.picturebackend.model.enums;

import lombok.Getter;
import cn.hutool.core.util.ObjUtil;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum RoleEnum {

    SYSTEM_ADMIN("系统管理员", "system-admin"),
    PUBLIC_SPACE_PICTURE_OWNER("公共空间图片拥有者", "public-space-picture-owner"),
    PRIVATE_SPACE_OWNER("私人空间拥有者", "private-space-owner"),
    PUBLIC_SPACE_OWNER("公共空间拥有者", "public-space-owner"),
    TEAM_SPACE_VIEWER("团队空间浏览者", "viewer"),
    TEAM_SPACE_EDITOR("团队空间编辑者", "editor"),
    TEAM_SPACE_OWNER("团队空间拥有者", "admin");

    private final String text;  // 角色显示名称（中文）
    private final String value; // 角色标识符（kebab-case格式）

    RoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     * @param value 枚举值的 value（如 "team-space-editor"）
     * @return 对应的枚举值，未找到时返回 null
     */
    public static RoleEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (RoleEnum role : RoleEnum.values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        return null;
    }

    /**
     * 获取所有枚举的文本列表
     * @return 中文名称列表（如 ["系统管理员", "公共空间拥有者"...]）
     */
    public static List<String> getAllTexts() {
        return Arrays.stream(RoleEnum.values())
                .map(RoleEnum::getText)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有枚举的值列表
     * @return kebab-case标识符列表（如 ["system-admin", "public-space-owner"...]）
     */
    public static List<String> getAllValues() {
        return Arrays.stream(RoleEnum.values())
                .map(RoleEnum::getValue)
                .collect(Collectors.toList());
    }
}