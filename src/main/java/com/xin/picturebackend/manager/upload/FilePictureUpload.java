package com.xin.picturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.xin.picturebackend.exception.BusinessException;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * TODO
 *
 * @author 黄兴鑫
 * @since 2025/3/10 16:48
 */
@Service
public class FilePictureUpload extends PictureUploadTemplate<MultipartFile> {
    @Override
    protected String validatePicture(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空！");
        final long SINGLE_PICTURE_MAX_SIZE = 2 * 1024 * 1024L;
        ThrowUtils.throwIf(multipartFile.getSize() > SINGLE_PICTURE_MAX_SIZE, ErrorCode.PARAMS_ERROR, "文件大小不能超过2MB！");
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp");
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件类型错误！");
        return fileSuffix;
    }

    @Override
    protected String getOriginalFilename(MultipartFile resourceSource, String suffix) {
        return resourceSource.getOriginalFilename();
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
