package com.xin.picturebackend.service.statemachine.states;

public enum ImageReviewState {
    PENDING_REVIEW,
    AI_PASS,
    AI_REJECTED,
    AI_SUSPICIOUS,
    MANUAL_PASS,
    MANUAL_REJECTED,
    FINAL_APPROVED,
    FINAL_REJECTED,
    APPEAL_PENDING,
    APPEAL_PASS,
    APPEAL_REJECTED
}
