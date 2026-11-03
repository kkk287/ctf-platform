package com.example.ctf.security;

/**
 * JWT 吊销登记处：登出后将令牌 jti 标记为失效，直至其自然过期。
 * 默认内存实现（单实例）；app.redis.enabled=true 时切换 Redis 实现（多实例共享）。
 */
public interface TokenRegistry {

    /**
     * 吊销指定 jti 的令牌，ttlSeconds 为令牌剩余有效期（秒）。
     */
    void revoke(String jti, long ttlSeconds);

    /**
     * 判断指定 jti 的令牌是否已被吊销。
     */
    boolean isRevoked(String jti);
}
