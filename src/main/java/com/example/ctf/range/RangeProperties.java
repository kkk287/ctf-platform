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

    /** host=连接 Docker 守护进程的地址；hostAddress=应用访问靶机时使用的宿主地址。 */
    public record Docker(String host, String hostAddress) {}
}
