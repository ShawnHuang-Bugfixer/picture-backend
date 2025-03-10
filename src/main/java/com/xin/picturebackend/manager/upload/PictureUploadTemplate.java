package com.xin.picturebackend.manager.upload;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.xin.picturebackend.config.CosClientConfig;
import com.xin.picturebackend.manager.CosManager;
import com.xin.picturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * 模板方法类，规定统一图片上传流程
 *
 * @author 黄兴鑫
 * @since 2025/3/10 16:23
 */
@Slf4j
public abstract class PictureUploadTemplate<T> {
    @Resource
    private CosManager cosManager;
    @Resource
    private CosClientConfig cosClientConfig;

    /**
     * 校验资源是否符合图片格式，如果符合返回文件后缀。
     *
     * @param resourceSource 资源
     * @return 返回文件后缀
     */
    protected abstract String validatePicture(T resourceSource);

    /**
     * 从资源中获取文件原始文件名
     *
     * @param resourceSource 资源
     * @return 返回保留后缀的原始文件名
     */
    protected abstract String getOriginalFilename(T resourceSource, String suffix);

    /**
     * 将资源写入临时文件，该临时文件被上传到 COS 对象存储
     *
     * @param resourceSource 原始资源
     * @param file           写入的目标临时文件
     */
    protected abstract void writeToTempFile(T resourceSource, File file);

    /**
     * 获取图片信息封装为 UploadPictureResult 对象返回
     *
     * @param imageInfo        图片信息，图片上传到 COS 后获取。
     * @param file             存储图片的临时文件
     * @param originalFilename 包含后缀的原始文件名
     * @param uploadPath       构造的封装路径
     * @return 返回UploadPictureResult 对象
     */
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

    public final UploadPictureResult uploadPicture(T resourceSource, String uploadPathPrefix) {
        // 1. 文件校验
        String fileSuffix = validatePicture(resourceSource);
        String originalFilename = getOriginalFilename(resourceSource, fileSuffix);
        // 2. 存储路径构造
        String uuid = RandomUtil.randomString(16);
        String uploadFileName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid, FileUtil.getSuffix(originalFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);
        File file = null;
        try {
            file = File.createTempFile(uploadPath, null);
            // 3. 将 picture 写入临时文件，将临时文件上传至 COS 对象存储并获取图片信息
            writeToTempFile(resourceSource, file);
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

    /**
     * 删除临时文件
     */
    private void deleteTempFile(File file, String uploadPath) {
        if (file != null) {
            if (!file.delete()) {
                log.error("file delete error, filepath: {}", uploadPath);
            }
        }
    }
}
