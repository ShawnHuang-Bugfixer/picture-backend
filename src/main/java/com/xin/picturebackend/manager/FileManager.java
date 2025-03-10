package com.xin.picturebackend.manager;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.xin.picturebackend.config.CosClientConfig;
import com.xin.picturebackend.exception.ErrorCode;
import com.xin.picturebackend.exception.ThrowUtils;
import com.xin.picturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 *
 * @author 黄兴鑫
 * @since 2025/2/27 16:27
 */
@Service
@Slf4j
@Deprecated
public class FileManager {
    @Resource
    private CosManager cosManager;
    @Resource
    private CosClientConfig cosClientConfig;

    /**
     * 将文件上传到指定路径前缀的位置，并返回图片信息
     * @param multipartFile 接收图片类型参数
     * @param uploadPathPrefix 上传图片的文件路径前缀
     * @return 返回图片信息
     */
    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        // 1. 文件校验
        validatePicture(multipartFile);
        String originalFilename = multipartFile.getOriginalFilename();
        // 2. 存储路径构造
        String uuid = RandomUtil.randomString(16);
        String uploadFileName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid, FileUtil.getSuffix(originalFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);
        File file = null;
        try {
            file = File.createTempFile(uploadPath, null);
            // 3. 构造临时文件用于 COS 对象存储
            multipartFile.transferTo(file);
            ImageInfo imageInfo = cosManager.putPictureObject(uploadPath, file).getCiUploadResult().getOriginalInfo().getImageInfo();
            // 4. 封装图片信息构造 UploadPictureResult 对象返回
            return getUploadPictureResult(imageInfo, file, originalFilename, uploadPath);
        } catch (IOException e) {
            log.error("file upload error, filepath: {}", uploadPath, e);
            throw new RuntimeException(e);
        } finally {
            deleteTempFile(file, uploadPath);
        }
    }

    private UploadPictureResult getUploadPictureResult(ImageInfo imageInfo, File file, String originalFilename, String uploadPath) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int width = imageInfo.getWidth();
        int height = imageInfo.getHeight();
        double picScale = NumberUtil.round(width * 1.0 / height, 2).doubleValue();
        uploadPictureResult.setPicWidth(width);
        uploadPictureResult.setPicHeight(height);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicSize(FileUtil.size(file));
        uploadPictureResult.setPicName(originalFilename);
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        return uploadPictureResult;
    }

    private static void deleteTempFile(File file, String uploadPath) {
        if (file != null) {
            if (!file.delete()) {
                log.error("file delete error, filepath: {}", uploadPath);
            }
        }
    }

    /**
     * 校验文件格式，大小
     * @param multipartFile 传入图片格式
     */
    public void validatePicture(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空！");
        final long SINGLE_PICTURE_MAX_SIZE = 2 * 1024 * 1024L;
        ThrowUtils.throwIf(multipartFile.getSize() > SINGLE_PICTURE_MAX_SIZE, ErrorCode.PARAMS_ERROR, "文件大小不能超过2MB！");
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp");
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件类型错误！");
    }
}
