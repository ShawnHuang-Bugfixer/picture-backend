package com.xin.picturebackend.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.xin.picturebackend.config.CosClientConfig;
import com.xin.picturebackend.manager.CosManager;
import com.xin.picturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

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
        uploadPictureResult.setPicColor(imageInfo.getAve());
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        uploadPictureResult.setThumbnailUrl(uploadPictureResult.getUrl());
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
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();
            if (CollUtil.isNotEmpty(objectList)) {
                CIObject compressedCiObject = objectList.get(0);
                CIObject thumbnailCiObject = compressedCiObject;
                // 有生成缩略图，才得到缩略图
                if (objectList.size() > 1) {
                    thumbnailCiObject = objectList.get(1);
                }
                // 封装压缩图返回结果
                return buildResult(originalFilename, compressedCiObject, thumbnailCiObject, imageInfo);
            }
            // 4. 封装图片信息构造 UploadPictureResult 对象返回
            return getUploadPictureResult(imageInfo, file, originalFilename, uploadPath);
        } catch (IOException e) {
            log.error("file upload error, filepath: {}", uploadPath, e);
            throw new RuntimeException(e);
        } finally {
            deleteTempFile(file, uploadPath);
        }
    }

    private UploadPictureResult buildResult(String originFilename, CIObject compressedCiObject, CIObject thumbnail, ImageInfo imageInfo) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(originFilename);
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressedCiObject.getFormat());
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());
        uploadPictureResult.setPicColor(getStandardRGB(imageInfo.getAve()));
        // 设置图片为压缩后 .webp 的地址
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressedCiObject.getKey());
        // 设置图片缩略图地址
        uploadPictureResult.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnail.getKey());
        return uploadPictureResult;
    }

    /**
     * 从缩写 RGB 中获取标准 RGB
     */
    private static String getStandardRGB(String rgb) {
        if (rgb == null || rgb.isEmpty()) {
            return "0x000000"; // 默认返回黑色
        }

        // 检查是否以 "0x" 开头
        if (!rgb.startsWith("0x")) {
            return "0x000000"; // 如果不是 "0x" 开头，返回默认值
        }

        // 移除 "0x" 前缀
        String hexValue = rgb.substring(2);

        // 检查是否为有效的十六进制字符
        if (!hexValue.matches("[0-9a-fA-F]+")) {
            return "0x000000"; // 默认返回黑色
        }

        // 处理简写形式
        if (hexValue.length() == 3) {
            // 标准简写形式，扩展为 6 位
            StringBuilder standardRGB = new StringBuilder("0x");
            for (char c : hexValue.toCharArray()) {
                standardRGB.append(c).append(c);
            }
            return standardRGB.toString();
        } else if (hexValue.length() == 6) {
            // 已经是标准格式，直接返回
            return rgb;
        } else {
            // 非标准长度，尝试扩展或截取
            if (hexValue.length() < 6) {
                // 扩展为 6 位，缺失的部分用 "0" 填充
                StringBuilder standardRGB = new StringBuilder("0x" + hexValue);
                while (standardRGB.length() < 8) { // "0x" + 6 位 = 8 位
                    standardRGB.append("0");
                }
                return standardRGB.toString();
            } else {
                // 截取前 6 位
                return "0x" + hexValue.substring(0, 6);
            }
        }
    }

    /**
     * 删除临时文件
     */
    private static void deleteTempFile(File file, String uploadPath) {
        if (file != null) {
            if (!file.delete()) {
                log.error("file delete error, filepath: {}", uploadPath);
            }
        }
    }

    public static void main(String[] args) {
        String rgb = "0xF00";
        System.out.println(getStandardRGB(rgb));
    }
}
