package com.example.ctf.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** 注册原生 WebSocket 端点 /ws（非 STOMP，前端零额外依赖）。 */
@Configuration
@EnableWebSocket
public class RealtimeWebSocketConfig implements WebSocketConfigurer {

    private final RealtimeWebSocketHandler handler;
    private final JwtHandshakeInterceptor handshakeInterceptor;

    public RealtimeWebSocketConfig(RealtimeWebSocketHandler handler,
                                   JwtHandshakeInterceptor handshakeInterceptor) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .addInterceptors(handshakeInterceptor)
                // token 已鉴权，放通跨域便于本地 Vite 代理与独立大屏
                .setAllowedOriginPatterns("*");
    }
}
