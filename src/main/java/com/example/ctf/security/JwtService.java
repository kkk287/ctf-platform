package com.example.ctf.security;

import com.example.ctf.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    @Value("${app.jwt.secret:}")
    private String configuredSecret;

    @Value("${app.jwt.expiration-hours:8}")
    private long expirationHours;

    private SecretKey key;

    @PostConstruct
    void init() {
        if (configuredSecret != null && configuredSecret.getBytes(StandardCharsets.UTF_8).length >= 32) {
            key = Keys.hmacShaKeyFor(configuredSecret.getBytes(StandardCharsets.UTF_8));
        } else {
            // 本地开箱即用：未配置密钥时生成进程级随机密钥，重启后旧 token 失效
            key = Jwts.SIG.HS256.key().build();
            log.warn("JWT_SECRET 未配置或长度不足 32 字节，已生成临时随机密钥；生产环境请通过环境变量配置固定密钥");
        }
    }

    public String createToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                // jti 用于登出吊销：黑名单按 jti 记录
                .id(UUID.randomUUID().toString())
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) throws JwtException {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /**
     * 令牌剩余有效秒数，登出吊销时作为黑名单记录的 TTL。
     */
    public long remainingSeconds(Claims claims) {
        Date expiration = claims.getExpiration();
        if (expiration == null) {
            return 0;
        }
        return Math.max(0, (expiration.getTime() - System.currentTimeMillis()) / 1000);
    }

    public long expiresInSeconds() {
        return expirationHours * 3600;
    }
}
