package com.xin.picturebackend.model.dto.sr;

import com.xin.picturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 超分任务分页查询请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SrTaskQueryRequest extends PageRequest implements Serializable {

    private Long id;

    private String taskNo;

    private String status;

    @Serial
    private static final long serialVersionUID = 1L;
}

