package com.example.ctf.domain;

public enum EventStatus {
    NOT_STARTED("未开始"), ONGOING("进行中"), ENDED("已结束");

    private final String label;

    EventStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
