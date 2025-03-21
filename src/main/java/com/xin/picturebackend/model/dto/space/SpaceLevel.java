package com.xin.picturebackend.model.dto.space;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 *
 * @author 黄兴鑫
 * @since 2025/3/20 22:26
 */
@Data
@AllArgsConstructor
public class SpaceLevel {

    private int value;

    private String text;

    private long maxCount;

    private long maxSize;
}
