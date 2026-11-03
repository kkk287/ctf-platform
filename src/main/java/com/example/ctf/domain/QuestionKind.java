package com.example.ctf.domain;

/** 理论赛题型 */
public enum QuestionKind {
    SINGLE("单选"), MULTI("多选"), JUDGE("判断");

    private final String label;

    QuestionKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
