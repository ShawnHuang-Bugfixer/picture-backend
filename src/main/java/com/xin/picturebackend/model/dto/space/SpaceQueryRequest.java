package com.xin.picturebackend.model.dto.space;

import com.xin.picturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * fixme @EqualsAndHashCode(callSuper = true) 何用?
 * @author 黄兴鑫
 * @since 2025/3/20 11:32
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SpaceQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    private static final long serialVersionUID = 1L;
}

