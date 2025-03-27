package com.xin.picturebackend.model.dto.spaceuser;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 *
 * @author 黄兴鑫
 * @since 2025/3/27 17:36
 */
@Data
public class SpaceUserEditRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 空间角色：viewer/editor/admin
     */
    private String spaceRole;

    @Serial
    private static final long serialVersionUID = 1L;
}
