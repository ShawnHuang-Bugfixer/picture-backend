package com.xin.picturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 *
 * @author 黄兴鑫
 * @since 2025/3/24 9:35
 */
@Data
public class SearchPictureByPictureRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    @Serial
    private static final long serialVersionUID = 1L;
}
