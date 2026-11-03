package com.example.ctf.realtime;

import com.example.ctf.security.JwtService;
import com.example.ctf.security.TokenRegistry;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手鉴权：与 REST 一致的 JWT 方案。
 * token 通过 query 参数传递（浏览器 WebSocket 无法自定义 Authorization 头），
 * 校验签名、吊销状态与账号可用性，通过后把用户信息写入会话属性。
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final JwtService jwtService;
    private final TokenRegistry tokenRegistry;
    private final UserDetailsService userDetailsService;

    public JwtHandshakeInterceptor(JwtService jwtService, TokenRegistry tokenRegistry,
                                   UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.tokenRegistry = tokenRegistry;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (token == null || token.isBlank()) {
            log.debug("WS 握手缺少 token，已拒绝");
            return false;
        }
        try {
            Claims claims = jwtService.parse(token);
            if (tokenRegistry.isRevoked(claims.getId())) {
                log.debug("WS 握手使用已吊销 token，已拒绝");
                return false;
            }
            UserDetails user = userDetailsService.loadUserByUsername(claims.getSubject());
            if (!user.isEnabled()) {
                log.debug("WS 握手账号不可用，已拒绝 user={}", claims.getSubject());
                return false;
            }
            attributes.put("userId", claims.get("uid", String.class));
            attributes.put("username", claims.getSubject());
            attributes.put("role", claims.get("role", String.class));
            return true;
        } catch (Exception ex) {
            log.debug("WS 握手 token 校验失败: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}
