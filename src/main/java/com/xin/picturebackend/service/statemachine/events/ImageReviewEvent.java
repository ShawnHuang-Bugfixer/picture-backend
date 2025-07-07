package com.xin.picturebackend.service.statemachine.events;

/**
 * @author 黄兴鑫
 * @since 2025/7/4 13:42
 */
public enum ImageReviewEvent {
    AI_REVIEW_PASS,
    AI_REVIEW_REJECT,
    AI_REVIEW_SUSPICIOUS,
    MANUAL_REVIEW_PASS,
    MANUAL_REVIEW_REJECT,
    APPEAL_SUBMIT,
    APPEAL_PASS,
    APPEAL_REJECT,
}
