package com.xin.picturebackend.imagesearch.model;

import lombok.Data;

/**
 *
 * @author 黄兴鑫
 * @since 2025/3/24 11:24
 */
@Data
public class ImageSearchResult {

    /**
     * 缩略图地址
     */
    private String thumbUrl;

    /**
     * 来源地址
     */
    private String fromUrl;
}
