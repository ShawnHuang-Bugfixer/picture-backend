package com.xin.picturebackend.model.enums;

import lombok.Getter;
import cn.hutool.core.util.ObjUtil;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author 黄兴鑫
 * @since 2025/3/28 17:13
 */
@Getter
public enum PermissionEnum {

    // 全局权限
    ADMIN_UPDATE_IMAGE("管理员更新图片", "admin.update.image"),
    ADMIN_ANALYZE_PERMISSIONS("分析系统权限", "admin.analyze.permissions"),
    USER_MANAGE("用户管理权限", "admin.user.manage"),

    // 公共空间权限
    PUBLIC_VIEW_IMAGE("查看公共空间图片", "public.view.image"),
    PUBLIC_UPLOAD_IMAGE("上传公共空间图片", "public.upload.image"),
    PUBLIC_MODIFY_IMAGE("修改公共空间图片", "public.modify.image"),
    PUBLIC_DELETE_IMAGE("删除公共空间图片", "public.delete.image"),

    // 私人空间权限
    PRIVATE_VIEW_IMAGE("查看私人空间图片", "private.view.image"),
    PRIVATE_UPLOAD_IMAGE("上传私人空间图片", "private.upload.image"),
    PRIVATE_MODIFY_IMAGE("修改私人空间图片", "private.modify.image"),
    PRIVATE_DELETE_IMAGE("删除私人空间图片", "private.delete.image"),
    PRIVATE_ANALYZE_PERMISSIONS("分析私人空间权限", "private.analyze.permissions"),

    // 团队空间权限
    TEAM_VIEW_IMAGE("查看团队空间图片", "team.view.image"),
    TEAM_UPLOAD_IMAGE("上传团队空间图片", "team.upload.image"),
    TEAM_MODIFY_IMAGE("修改团队空间图片", "team.modify.image"),
    TEAM_DELETE_IMAGE("删除团队空间图片", "team.delete.image"),
    TEAM_MANAGE_MEMBERS("管理团队成员", "team.manage.members"),
    TEAM_ANALYZE_PERMISSIONS("分析团队空间权限", "team.analyze.permissions");

    private final String text;  // 权限描述（中文）
    private final String value; // 权限标识符（kebab.case格式）

    PermissionEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 权限标识符（如 "team.upload.image"）
     * @return 对应的权限枚举，未找到时返回 null
     */
    public static PermissionEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (PermissionEnum permission : PermissionEnum.values()) {
            if (permission.value.equals(value)) {
                return permission;
            }
        }
        return null;
    }
}
