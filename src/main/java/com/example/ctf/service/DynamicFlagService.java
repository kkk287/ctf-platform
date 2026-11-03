package com.example.ctf.service;

import com.example.ctf.domain.DynamicFlag;
import com.example.ctf.repository.DynamicFlagRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 动态 flag 生成与查询。
 * 明文格式：{@code flag{md5("ctf-sec:" + challengeId + ":" + ownerId)}}
 * 数据库只保存加盐 SHA-256，明文仅用于注入容器 env，从不落库也不返回给前端。
 */
@Service
public class DynamicFlagService {

    static final String PLAINTEXT_PREFIX = "ctf-sec:";   // 参与 md5 的前缀，与静态 flag 区分

    private final DynamicFlagRepository repository;
    private final FlagHasher flagHasher;

    public DynamicFlagService(DynamicFlagRepository repository, FlagHasher flagHasher) {
        this.repository = repository;
        this.flagHasher = flagHasher;
    }

    /** 生成明文 flag（仅用于注入容器 env，调用方必须尽快用完，不可持久化）。 */
    public String generatePlaintext(String challengeId, String ownerId) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            String input = PLAINTEXT_PREFIX + challengeId + ":" + ownerId;
            String digest = HexFormat.of().formatHex(md5.digest(input.getBytes(StandardCharsets.UTF_8)));
            return "flag{" + digest + "}";
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 不可用", ex);
        }
    }

    /** 将明文 flag 落库为加盐哈希；返回 salt + hash，供 InstanceService 组装 DynamicFlag 实体。 */
    public StoredFlag store(String plaintext) {
        String salt = flagHasher.newSalt();
        String hash = flagHasher.hash(plaintext, salt);
        return new StoredFlag(hash, salt);
    }

    /** 判分链路：按 challenge + owner 查最近一条动态 flag（活跃实例对应）。 */
    public Optional<DynamicFlag> resolve(String challengeId, String ownerId) {
        return repository.findFirstByOwnerIdAndChallengeIdOrderByCreatedAtDesc(ownerId, challengeId);
    }

    public Optional<DynamicFlag> resolveByInstance(String instanceId) {
        return repository.findByInstanceId(instanceId);
    }

    /** 比对提交与动态 flag（沿用 FlagHasher.matches）。 */
    public boolean matches(String submitted, DynamicFlag expected) {
        return flagHasher.matches(submitted, expected.getFlagHash(), expected.getFlagSalt());
    }

    public record StoredFlag(String flagHash, String flagSalt) {}
}
