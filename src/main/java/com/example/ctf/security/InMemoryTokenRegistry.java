package com.example.ctf.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版吊销登记处：适合单实例部署。条目到期后惰性清理，超过阈值时主动purge，避免无限增长。
 */
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryTokenRegistry implements TokenRegistry {

    private static final int PURGE_THRESHOLD = 10_000;

    /** jti -> 吊销记录的过期时间（epoch 秒），与令牌自然过期时间对齐 */
    private final Map<String, Long> revoked = new ConcurrentHashMap<>();

    @Override
    public void revoke(String jti, long ttlSeconds) {
        if (jti == null || ttlSeconds <= 0) {
            return;
        }
        if (revoked.size() > PURGE_THRESHOLD) {
            purgeExpired();
        }
        revoked.put(jti, Instant.now().getEpochSecond() + ttlSeconds);
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null) {
            return false;
        }
        Long expireAt = revoked.get(jti);
        if (expireAt == null) {
            return false;
        }
        if (expireAt <= Instant.now().getEpochSecond()) {
            revoked.remove(jti, expireAt);
            return false;
        }
        return true;
    }

    private void purgeExpired() {
        long now = Instant.now().getEpochSecond();
        revoked.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
