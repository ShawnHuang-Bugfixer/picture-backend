package com.xin.picturebackend.utils;

import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 从 url 中提取 COS OBJKey 的工具类。
 *
 * @author 黄兴鑫
 * @since 2025/7/8 14:43
 */
@Slf4j
public class COSKeyUtils {
    public static String cosOriginKeyHandler(String webpKey, String thumbnailKey) {
        int i = thumbnailKey.indexOf(".");
        String fileExtension = thumbnailKey.substring(i);
        String keyPrefix = webpKey.substring(0, webpKey.length() - 5);
        return keyPrefix + fileExtension;
    }

    public static String cosKeyHandler(String url, String cosClientHost) {
        if (!url.contains(cosClientHost)) {
            log.error("picture url: {} 和对象存储主机地址: {} 不一致。", url, cosClientHost);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "cosKey 处理错误，url 错误");
        }
        int i = url.indexOf(cosClientHost);
        return url.substring(i + cosClientHost.length());
    }

    public static String switchUrlFromHttpToHttps(String url) {
        if (url != null && url.startsWith("http://")) {
            url = url.replaceFirst("http://", "https://");
        }
        return url;
    }
}
