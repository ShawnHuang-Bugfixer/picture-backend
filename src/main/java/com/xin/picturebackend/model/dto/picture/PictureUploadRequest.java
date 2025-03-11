package com.xin.picturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 单张图片上传请求
 *
 * @author 黄兴鑫
 * @since 2025/2/27 18:36
 */
@Data
public class PictureUploadRequest implements Serializable {

    /**
     * 图片 id（用于修改）
     */
    private Long id;

    /**
     * 文件地址
     */
    private String fileUrl;

    /**
     * 图片名称
     */
    private String picName;

    private static final long serialVersionUID = 1L;
}

