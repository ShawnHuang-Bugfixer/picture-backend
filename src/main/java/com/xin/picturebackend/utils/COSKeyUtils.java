package com.xin.picturebackend.utils;

import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;

/**
 * TODO
 * @author 黄兴鑫
 * @since 2025/7/8 14:43
 */
public class COSKeyUtils {
    public static String cosOriginKeyHandler(String webpKey, String thumbnailKey) {
        int i = thumbnailKey.indexOf(".");
        String fileExtension = thumbnailKey.substring(i);
        String keyPrefix = webpKey.substring(0, webpKey.length() - 5);
        return keyPrefix + fileExtension;
    }

    public static String cosKeyHandler(String url, String cosClientHost) {
        ThrowUtils.throwIf(!url.contains(cosClientHost), ErrorCode.PARAMS_ERROR, "cosKey 处理错误，url 错误");
        int i = url.indexOf(cosClientHost);
        return url.substring(i + cosClientHost.length());
    }
}
