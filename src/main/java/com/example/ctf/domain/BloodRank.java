package com.example.ctf.domain;

public enum BloodRank {
    NONE("", 0),
    FIRST("一血", 20),
    SECOND("二血", 10),
    THIRD("三血", 5);

    private final String label;
    private final int bonusPercent;

    BloodRank(String label, int bonusPercent) {
        this.label = label;
        this.bonusPercent = bonusPercent;
    }

    public String label() {
        return label;
    }

    public int bonusPercent() {
        return bonusPercent;
    }

    public static BloodRank fromSolveOrder(int order) {
        return switch (order) {
            case 1 -> FIRST;
            case 2 -> SECOND;
            case 3 -> THIRD;
            default -> NONE;
        };
    }
}
