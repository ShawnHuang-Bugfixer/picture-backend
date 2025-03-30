package com.xin.picturebackend.auth.constant;

public interface PermissionConstants {
    // 全局权限
    String ADMIN_UPDATE_IMAGE = "admin.update.image";
    String ADMIN_ANALYZE_PERMISSIONS = "admin.analyze.permissions";
    String ADMIN_BATCH_UPLOAD_IMAGE = "admin.batchUpload.image";
    String USER_MANAGE = "admin.user.manage";

    // 公共空间权限
    String PUBLIC_VIEW_IMAGE = "public.view.image";
    String PUBLIC_UPLOAD_IMAGE = "public.upload.image";
    String PUBLIC_MODIFY_IMAGE = "public.modify.image";
    String PUBLIC_DELETE_IMAGE = "public.delete.image";

    // 私人空间权限
    String PRIVATE_VIEW_IMAGE = "private.view.image";
    String PRIVATE_UPLOAD_IMAGE = "private.upload.image";
    String PRIVATE_MODIFY_IMAGE = "private.modify.image";
    String PRIVATE_DELETE_IMAGE = "private.delete.image";
    String PRIVATE_ANALYZE_PERMISSIONS = "private.analyze.permissions";

    // 团队空间权限
    String TEAM_VIEW_IMAGE = "team.view.image";
    String TEAM_UPLOAD_IMAGE = "team.upload.image";
    String TEAM_MODIFY_IMAGE = "team.modify.image";
    String TEAM_DELETE_IMAGE = "team.delete.image";
    String TEAM_MANAGE_MEMBERS = "team.manage.members";
    String TEAM_ANALYZE_PERMISSIONS = "team.analyze.permissions";
}
