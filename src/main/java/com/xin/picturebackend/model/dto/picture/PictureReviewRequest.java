package com.xin.picturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新图片审核状态请求
 *
 * @author 黄兴鑫
 * @since 2025/3/10 12:42
 */
@Data
public class PictureReviewRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 状态：0-待审核, 1-通过, 2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;


    private static final long serialVersionUID = 1L;
}

