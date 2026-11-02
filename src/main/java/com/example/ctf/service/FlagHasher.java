package com.example.ctf.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * flag 哈希工具：数据库中只保存加盐 SHA-256，永不保存 flag 明文。
 */
@Component
public class FlagHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    public String hash(String flag, String saltHex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((saltHex + ":").getBytes(StandardCharsets.UTF_8));
            digest.update(normalize(flag).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    public boolean matches(String flag, String expectedHash, String saltHex) {
        byte[] actual = hash(flag, saltHex).getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }

    /** 提交审计用摘要（不加盐，便于发现同一条错误 flag 被多账号提交），同样不落明文 */
    public String submissionHash(String flag) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalize(flag).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private String normalize(String flag) {
        return flag == null ? "" : flag.trim();
    }
}
