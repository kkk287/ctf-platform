package com.example.ctf.range;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 无 Docker 环境兜底实现：在内存里用 {@link ConcurrentHashMap} 模拟容器生命周期。
 * 宿主端口从配置的 {@code portBridgeStart} 起递增，按 owner + challenge 维度唯一。
 * <p>
 * 与 {@link DockerOrchestrator} 互斥：仅在 {@code app.range.enabled} 未开启时注册。
 */
@Component
@ConditionalOnProperty(name = "app.range.enabled", havingValue = "false", matchIfMissing = true)
public class NoopOrchestrator implements RangeOrchestrator {

    private final Map<String, String> running = new ConcurrentHashMap<>();  // containerId -> spec 描述
    private final AtomicInteger portCursor;
    private final RangeProperties props;

    public NoopOrchestrator(RangeProperties props) {
        this.props = props;
        this.portCursor = new AtomicInteger(Math.max(props.portBridgeStart(), 18000));
    }

    @Override
    public OrchestratorInstance start(InstanceSpec spec) {
        int hostPort = spec.hostPort() != null && spec.hostPort() > 0
                ? spec.hostPort() : nextPort();
        String containerId = "noop-" + spec.instanceId();
        running.put(containerId, spec.challengeId() + ":" + spec.ownerId());
        String host = "localhost";
        return new OrchestratorInstance(containerId, containerId, host, hostPort, LocalDateTime.now());
    }

    @Override
    public void stop(String containerId) {
        running.remove(containerId);
    }

    @Override
    public boolean isRunning(String containerId) {
        return running.containsKey(containerId);
    }

    @Override
    public String backendName() {
        return "noop";
    }

    private int nextPort() {
        int port = portCursor.incrementAndGet();
        if (port > props.portBridgeEnd()) {
            port = props.portBridgeStart();
            portCursor.set(port);
        }
        return port;
    }
}
