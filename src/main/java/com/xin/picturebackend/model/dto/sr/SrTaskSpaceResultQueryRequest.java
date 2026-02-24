package com.xin.picturebackend.model.dto.sr;

import com.xin.picturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 团队空间超分结果分页查询请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SrTaskSpaceResultQueryRequest extends PageRequest implements Serializable {

    private Long spaceId;

    private String taskNo;

    private String modelName;

    private Date startTime;

    private Date endTime;

    @Serial
    private static final long serialVersionUID = 1L;
}

