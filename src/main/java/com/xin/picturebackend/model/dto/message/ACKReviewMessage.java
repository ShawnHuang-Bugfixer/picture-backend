package com.xin.picturebackend.model.dto.message;

import lombok.Data;

/**
 *
 * @author 黄兴鑫
 * @since 2025/6/30 14:58
 */
@Data
public class ACKReviewMessage {
    private Long userId;
    private Long msgId;
}
