package com.xin.picturebackend.model.messagequeue.messagepush;

public enum MessageType {
    REVIEW("review");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}