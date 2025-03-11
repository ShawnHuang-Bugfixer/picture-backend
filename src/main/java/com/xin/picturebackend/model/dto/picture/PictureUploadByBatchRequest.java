package com.xin.picturebackend.model.dto.picture;

import lombok.Data;

/**
 * 批量拉取图片的请求，包含搜索词、抓取数量、名称前缀
 *
 * @author 黄兴鑫
 * @since 2025/3/11 14:39
 */
@Data
public class PictureUploadByBatchRequest {

    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 抓取数量
     */
    private Integer count = 30;

    /**
     * 名称前缀
     */
    private String namePrefix;

}
