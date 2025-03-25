package com.xin.picturebackend.model.dto.picture;

import com.xin.picturebackend.apiintegration.aliyunai.model.CreateOutPaintingTaskRequest;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 *
 * @author 黄兴鑫
 * @since 2025/3/25 15:31
 */
@Data
public class CreatePictureOutPaintingTaskRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 扩图参数
     */
    private CreateOutPaintingTaskRequest.Parameters parameters;

    @Serial
    private static final long serialVersionUID = 1L;
}
