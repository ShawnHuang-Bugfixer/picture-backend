package com.xin.picturebackend.model.dto.space;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 *
 * @author 黄兴鑫
 * @since 2025/3/26 15:02
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SpaceUserAnalyzeRequest extends SpaceAnalyzeRequest {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 时间维度：day / week / month
     */
    private String timeDimension;
}

