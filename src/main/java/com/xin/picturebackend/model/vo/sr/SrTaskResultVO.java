package com.xin.picturebackend.model.vo.sr;

import com.xin.picturebackend.model.entity.SrTaskResult;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 超分结果视图
 */
@Data
public class SrTaskResultVO implements Serializable {

    private Long id;

    private Long taskId;

    private String taskNo;

    private Long userId;

    private Long spaceId;

    private String bizType;

    private String modelName;

    private String modelVersion;

    private String inputFileKey;

    private String inputFileUrl;

    private String outputFileKey;

    private String outputUrl;

    private String outputFormat;

    private Long outputSize;

    private Integer outputWidth;

    private Integer outputHeight;

    private Long durationMs;

    private BigDecimal fps;

    private Integer bitrateKbps;

    private String codec;

    private Long costMs;

    private Integer attempt;

    private String traceId;

    private Date createTime;

    private Date updateTime;

    @Serial
    private static final long serialVersionUID = 1L;

    public static SrTaskResultVO objToVo(SrTaskResult srTaskResult) {
        if (srTaskResult == null) {
            return null;
        }
        SrTaskResultVO srTaskResultVO = new SrTaskResultVO();
        BeanUtils.copyProperties(srTaskResult, srTaskResultVO);
        return srTaskResultVO;
    }
}
