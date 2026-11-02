package com.example.ctf.domain;

public enum EventType {
    COMPETITION("赛事"), PRACTICE("练习");

    private final String label;

    EventType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
