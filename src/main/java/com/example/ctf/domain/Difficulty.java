package com.example.ctf.domain;

public enum Difficulty {
    EASY("简单"), MEDIUM("中等"), HARD("困难");

    private final String label;

    Difficulty(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
