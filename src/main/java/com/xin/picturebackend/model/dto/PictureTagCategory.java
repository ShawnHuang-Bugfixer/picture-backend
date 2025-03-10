package com.xin.picturebackend.model.dto;

import lombok.Data;

import java.util.List;

/**
 * TODO
 *
 * @author 黄兴鑫
 * @since 2025/2/28 10:49
 */
@Data
public class PictureTagCategory {
    private List<String> tagList;

    private List<String> categoryList;
}
