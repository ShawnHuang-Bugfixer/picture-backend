package com.xin.picturebackend.messagepush.model;

public enum MessageType {
    ACTIVITY("activity"),
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