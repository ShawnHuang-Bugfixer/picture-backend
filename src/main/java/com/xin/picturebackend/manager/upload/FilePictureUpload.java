package com.xin.picturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * 图片文件上传
 *
 * @author 黄兴鑫
 * @since 2025/3/10 16:48
 */
@Service
public class FilePictureUpload extends PictureUploadTemplate<MultipartFile> {
    @Override
    protected String validatePicture(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空！");
        final long SINGLE_PICTURE_MAX_SIZE = 8 * 1024 * 1024L;
        ThrowUtils.throwIf(multipartFile.getSize() > SINGLE_PICTURE_MAX_SIZE, ErrorCode.PARAMS_ERROR, "文件大小不能超过8MB！");
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp");
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件类型错误！");
        return fileSuffix;
    }

    @Override
    protected String getOriginalFilename(MultipartFile resourceSource, String suffix) {
        String originalFilename = resourceSource.getOriginalFilename();
        ThrowUtils.throwIf(StrUtil.isBlank(originalFilename), ErrorCode.PARAMS_ERROR, "文件名不能为空！");
        ThrowUtils.throwIf(originalFilename.length() > 100, ErrorCode.PARAMS_ERROR, "文件名长度不能超过100个字符！");
        return originalFilename;
    }

    @Override
    protected void writeToTempFile(MultipartFile resourceSource, File file) {
        try {
            resourceSource.transferTo(file);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败！");
        }
    }
}
