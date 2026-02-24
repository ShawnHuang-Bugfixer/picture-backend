package com.xin.picturebackend.model.dto.sr;

import com.xin.picturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 超分结果分页查询请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SrTaskResultQueryRequest extends PageRequest implements Serializable {

    private String taskNo;

    private String bizType;

    private String modelName;

    private Date startTime;

    private Date endTime;

    @Serial
    private static final long serialVersionUID = 1L;
}

