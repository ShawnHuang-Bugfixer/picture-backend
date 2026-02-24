package com.xin.picturebackend.model.vo.sr;

import com.xin.picturebackend.model.entity.SrTask;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 超分任务视图
 */
@Data
public class SrTaskVO implements Serializable {

    private Long id;

    private String taskNo;

    private Long userId;

    private Long pictureId;

    private String inputFileKey;

    private String outputFileKey;

    private String status;

    private Integer progress;

    private Integer scale;

    private String modelName;

    private String modelVersion;

    private Integer attempt;

    private Long costMs;

    private String errorCode;

    private String errorMsg;

    private String traceId;

    private Date createTime;

    private Date updateTime;

    @Serial
    private static final long serialVersionUID = 1L;

    public static SrTaskVO objToVo(SrTask srTask) {
        if (srTask == null) {
            return null;
        }
        SrTaskVO srTaskVO = new SrTaskVO();
        BeanUtils.copyProperties(srTask, srTaskVO);
        return srTaskVO;
    }
}

