package com.xin.picturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;
@Getter
public enum PictureReviewStatusEnum {
    PENDING_REVIEW("待审核", 0),
    AI_PASS("机审通过", 1),
    AI_REJECTED("机审拒绝", 2),
    AI_SUSPICIOUS("机审存疑", 3),
    MANUAL_PASS("人工复审通过", 4),
    MANUAL_REJECTED("人工复审拒绝", 5),
    FINAL_APPROVED("最终通过", 6),
    FINAL_REJECTED("最终拒绝", 7),
    APPEAL_PENDING("申诉中", 8),
    APPEAL_PASS("申诉通过", 9),
    APPEAL_REJECTED("申诉拒绝", 10);

    private final String text;
    private final int value;

    PictureReviewStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     */
    public static PictureReviewStatusEnum getEnumByValue(Integer value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (PictureReviewStatusEnum status : PictureReviewStatusEnum.values()) {
            if (status.value == value) {
                return status;
            }
        }
        return null;
    }
}


