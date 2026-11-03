package com.example.ctf.range;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.range.*} 配置绑定。 */
@ConfigurationProperties(prefix = "app.range")
public record RangeProperties(
        boolean enabled,
        Docker docker,
        int maxInstancesPerUser,
        int maxInstancesGlobal,
        int defaultTtlMinutes,
        int portBridgeStart,
        int portBridgeEnd,
        long sweepIntervalMs) {

    public record Docker(String host) {}
}
