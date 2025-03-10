package com.xin.picturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * 从 URL 中抓取网络资源
 *
 * @author 黄兴鑫
 * @since 2025/3/10 17:19
 */
@Service
public class URLPictureUpload extends PictureUploadTemplate<String>{
    @Override
    protected String validatePicture(String resourceSource) {
        // 1. 校验资源地址
        ThrowUtils.throwIf(StringUtils.isBlank(resourceSource), ErrorCode.PARAMS_ERROR, "资源地址不能为空！");
        ThrowUtils.throwIf(!resourceSource.startsWith("http://") && !resourceSource.startsWith("https://"), ErrorCode.PARAMS_ERROR, "资源地址格式错误！");
        try {
            new URL(resourceSource);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源地址格式错误！");
        }
        // 2. 校验资源类型
        try (HttpResponse response = HttpUtil.createRequest(Method.HEAD, resourceSource).execute()) {
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持 Head 请求或资源地址错误!");
            }
            String contentType = response.header("Content-Type");
            if (StringUtils.isBlank(contentType) || !contentType.startsWith("image/")) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误！");
            }
            final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
            ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType), ErrorCode.PARAMS_ERROR, "文件类型错误！");
            // 3. 校验文件大小
            ThrowUtils.throwIf(response.contentLength() > 1024 * 1024 * 2, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2MB！");
            return contentType.substring(contentType.indexOf("/") + 1);
        }
    }

    @Override
    protected String getOriginalFilename(String resourceSource, String suffix) {
        return FileUtil.mainName(resourceSource) + "." + suffix;
    }

    @Override
    protected void writeToTempFile(String resourceSource, File file) {
        HttpUtil.downloadFile(resourceSource, file);
    }
}
