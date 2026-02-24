package com.xin.picturebackend.model.enums;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;

/**
 * 超分任务状态
 */
@Getter
public enum SrTaskStatusEnum {
    CREATED("CREATED"),
    QUEUED("QUEUED"),
    RUNNING("RUNNING"),
    SUCCEEDED("SUCCEEDED"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED");

    private final String value;

    SrTaskStatusEnum(String value) {
        this.value = value;
    }

    public static SrTaskStatusEnum getByValue(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        for (SrTaskStatusEnum statusEnum : values()) {
            if (statusEnum.value.equals(value)) {
                return statusEnum;
            }
        }
        return null;
    }
}

