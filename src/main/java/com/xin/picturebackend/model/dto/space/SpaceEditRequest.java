package com.xin.picturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 *
 * @author 黄兴鑫
 * @since 2025/3/20 11:31
 */
@Data
public class SpaceEditRequest implements Serializable {

    /**
     * 空间 id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    private static final long serialVersionUID = 1L;
}

