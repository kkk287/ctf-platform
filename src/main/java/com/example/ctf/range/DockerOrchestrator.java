package com.example.ctf.range;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 生产实现：通过 docker-java 调宿主 Docker API。
 * <p>
 * 默认通过 compose 挂载的 unix socket（{@code /var/run/docker.sock}）连接；
 * 也可配 HTTP 直连（{@code http://host:2375} / {@code https://host:2376}）。
 * <p>
 * 未开启 {@code app.range.enabled=true} 时由 {@code @ConditionalOnProperty} 控制，
 * 自动退化为 {@link NoopOrchestrator}。
 */
@Component
@ConditionalOnProperty(name = "app.range.enabled", havingValue = "true")
public class DockerOrchestrator implements RangeOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DockerOrchestrator.class);
    private static final long STARTUP_TIMEOUT_MS = 30_000L;
    private static final long PULL_TIMEOUT_SEC = 120L;

    private final RangeProperties props;
    private final DockerClient client;

    public DockerOrchestrator(RangeProperties props) {
        this.props = props;
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(props.docker().host())
                .withDockerTlsVerify(false)
                .build();
        URI dockerHostUri = config.getDockerHost();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(dockerHostUri)
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(java.time.Duration.ofSeconds(5))
                .responseTimeout(java.time.Duration.ofSeconds(30))
                .build();
        // 返回接口 DockerClient（非具体类 DockerClientImpl）
        this.client = DockerClientImpl.getInstance(config, httpClient);
        log.info("DockerOrchestrator initialized, host={}", props.docker().host());
    }

    @Override
    public OrchestratorInstance start(InstanceSpec spec) {
        try {
            // 1. pull image（幂等，已存在时跳过）
            client.pullImageCmd(spec.image())
                    .exec(new PullImageResultCallback())
                    .awaitCompletion(PULL_TIMEOUT_SEC, TimeUnit.SECONDS);

            // 2. HostConfig：资源限额 / 端口映射 / autoRemove
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withRestartPolicy(RestartPolicy.noRestart())
                    .withAutoRemove(true);
            if (spec.memoryMb() != null && spec.memoryMb() > 0) {
                hostConfig.withMemory(spec.memoryMb() * 1024L * 1024L);
            }
            if (spec.cpuLimit() != null && !spec.cpuLimit().isBlank()) {
                hostConfig.withNanoCPUs((long) (Double.parseDouble(spec.cpuLimit()) * 1_000_000_000L));
            }
            ExposedPort exposedPort = ExposedPort.tcp(spec.containerPort());
            Ports.Binding hostBinding = (spec.hostPort() != null && spec.hostPort() > 0)
                    ? Ports.Binding.bindPort(spec.hostPort())
                    : Ports.Binding.empty();  // 让 Docker 自动分配
            hostConfig.withPortBindings(new PortBinding(hostBinding, exposedPort));

            // 3. create（labels 在 CreateContainerCmd 层面，不是 HostConfig）
            CreateContainerResponse create = client.createContainerCmd(spec.image())
                    .withName("ctf-inst-" + spec.instanceId())
                    .withLabels(spec.labels())
                    .withEnv(spec.env().entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue()).toList())
                    .withHostConfig(hostConfig)
                    .withExposedPorts(List.of(exposedPort))
                    .exec();
            String containerId = create.getId();

            // 4. start
            client.startContainerCmd(containerId).exec();

            // 5. 轮询 inspect 直到容器 Running 且端口映射出现
            long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;
            InspectContainerResponse inspect = null;
            while (System.currentTimeMillis() < deadline) {
                inspect = client.inspectContainerCmd(containerId).exec();
                if (Boolean.TRUE.equals(inspect.getState().getRunning())) {
                    Ports ports = inspect.getNetworkSettings().getPorts();
                    if (ports != null && !ports.getBindings().isEmpty()
                            && ports.getBindings().get(exposedPort) != null) {
                        break;
                    }
                }
                Thread.sleep(500);
            }
            if (inspect == null || !Boolean.TRUE.equals(inspect.getState().getRunning())) {
                log.warn("Container {} not running after create, will cleanup", containerId);
                safeRemove(containerId);
                throw new IllegalStateException("container failed to start: " + containerId);
            }

            Integer mappedPort = resolveHostPort(inspect, spec.containerPort());
            String host = "127.0.0.1";  // compose 默认 bridge 网络
            return new OrchestratorInstance(containerId, containerId.substring(0, 12), host, mappedPort, null);
        } catch (Exception ex) {
            log.error("Docker start failed for {}: {}", spec.instanceId(), ex.getMessage(), ex);
            throw new RuntimeException("range orchestrator error: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void stop(String containerId) {
        if (containerId == null || containerId.isBlank()) return;
        try { client.stopContainerCmd(containerId).withTimeout(5).exec(); }
        catch (Exception ex) { log.debug("stop container {} ignored: {}", containerId, ex.getMessage()); }
        safeRemove(containerId);
    }

    @Override
    public boolean isRunning(String containerId) {
        if (containerId == null) return false;
        try {
            InspectContainerResponse inspect = client.inspectContainerCmd(containerId).exec();
            return Boolean.TRUE.equals(inspect.getState().getRunning());
        } catch (Exception ex) { return false; }
    }

    @Override
    public String backendName() { return "docker"; }

    private void safeRemove(String containerId) {
        try { client.removeContainerCmd(containerId).withForce(true).exec(); } catch (Exception ignored) {}
    }

    private Integer resolveHostPort(InspectContainerResponse inspect, Integer containerPort) {
        Ports ports = inspect.getNetworkSettings().getPorts();
        if (ports == null) return null;
        Ports.Binding[] bindings = ports.getBindings().get(ExposedPort.tcp(containerPort));
        if (bindings == null || bindings.length == 0) return null;
        String hostPortSpec = bindings[0].getHostPortSpec();
        if (hostPortSpec == null || hostPortSpec.isBlank()) return null;
        try { return Integer.parseInt(hostPortSpec.split("-")[0]); } catch (NumberFormatException e) { return null; }
    }
}
