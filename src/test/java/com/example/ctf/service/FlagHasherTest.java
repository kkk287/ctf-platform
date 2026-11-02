package com.example.ctf.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlagHasherTest {

    private final FlagHasher hasher = new FlagHasher();

    @Test
    void hashIsStableForSameSaltAndDiffersForDifferentSalt() {
        String saltA = hasher.newSalt();
        String saltB = hasher.newSalt();
        String hash = hasher.hash("flag{demo}", saltA);

        assertThat(hasher.hash("flag{demo}", saltA)).isEqualTo(hash);
        assertThat(hasher.hash("flag{demo}", saltB)).isNotEqualTo(hash);
        assertThat(hash).doesNotContain("flag{demo}");
    }

    @Test
    void matchesTrimsOuterWhitespace() {
        String salt = hasher.newSalt();
        String hash = hasher.hash("flag{demo}", salt);

        assertThat(hasher.matches("flag{demo}", hash, salt)).isTrue();
        assertThat(hasher.matches("  flag{demo}  ", hash, salt)).isTrue();
        assertThat(hasher.matches("flag{wrong}", hash, salt)).isFalse();
    }

    @Test
    void submissionHashIsDeterministicAndDoesNotLeakPlaintext() {
        String digest = hasher.submissionHash(" flag{submission} ");

        assertThat(digest).isEqualTo(hasher.submissionHash("flag{submission}"));
        assertThat(digest).hasSize(64);
        assertThat(digest).doesNotContain("flag");
    }
}
