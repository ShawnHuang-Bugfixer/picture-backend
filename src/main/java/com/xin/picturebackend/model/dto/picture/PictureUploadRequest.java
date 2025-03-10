package com.xin.picturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * TODO
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

    private static final long serialVersionUID = 1L;
}

