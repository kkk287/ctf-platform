package com.example.ctf.service;

import com.example.ctf.domain.BloodRank;

/**
 * 动态积分规则：
 * base = max(minPoints, initialPoints - decayPoints * 已解出人数)
 * blood bonus = round(base * 一血20% / 二血10% / 三血5%)
 * final = max(0, base + bonus - 已解锁提示总代价)
 */
public final class ScoringPolicy {

    private ScoringPolicy() {
    }

    public static int basePoints(int initialPoints, int minPoints, int decayPoints, long solvedBefore) {
        long value = (long) initialPoints - (long) decayPoints * solvedBefore;
        return (int) Math.max(minPoints, value);
    }

    public static int bloodBonus(int basePoints, BloodRank bloodRank) {
        return (int) Math.round(basePoints * bloodRank.bonusPercent() / 100.0);
    }

    public static int finalPoints(int basePoints, int bloodBonus, int hintCost) {
        return Math.max(0, basePoints + bloodBonus - hintCost);
    }

    public static int currentPoints(int initialPoints, int minPoints, int decayPoints, long solveCount) {
        return basePoints(initialPoints, minPoints, decayPoints, solveCount);
    }
}
