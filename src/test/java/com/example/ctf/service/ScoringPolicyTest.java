package com.example.ctf.service;

import com.example.ctf.domain.BloodRank;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringPolicyTest {

    @Test
    void basePointsDecaysWithSolveCountAndNeverBelowMinimum() {
        assertThat(ScoringPolicy.basePoints(500, 100, 25, 0)).isEqualTo(500);
        assertThat(ScoringPolicy.basePoints(500, 100, 25, 4)).isEqualTo(400);
        assertThat(ScoringPolicy.basePoints(500, 100, 25, 30)).isEqualTo(100);
        assertThat(ScoringPolicy.basePoints(100, 0, 50, 5)).isZero();
    }

    @Test
    void bloodBonusUsesFirstSecondThirdPercentages() {
        assertThat(ScoringPolicy.bloodBonus(200, BloodRank.FIRST)).isEqualTo(40);
        assertThat(ScoringPolicy.bloodBonus(200, BloodRank.SECOND)).isEqualTo(20);
        assertThat(ScoringPolicy.bloodBonus(200, BloodRank.THIRD)).isEqualTo(10);
        assertThat(ScoringPolicy.bloodBonus(200, BloodRank.NONE)).isZero();
    }

    @Test
    void finalPointsNeverNegativeEvenWithExpensiveHints() {
        assertThat(ScoringPolicy.finalPoints(200, 40, 0)).isEqualTo(240);
        assertThat(ScoringPolicy.finalPoints(200, 0, 50)).isEqualTo(150);
        assertThat(ScoringPolicy.finalPoints(50, 10, 200)).isZero();
    }
}
