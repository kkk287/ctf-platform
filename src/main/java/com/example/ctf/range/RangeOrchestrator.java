package com.example.ctf.range;

/**
 * 靶场调度 SPI。默认 NoopOrchestrator（无 Docker 环境 / 测试兜底），生产切换 DockerOrchestrator。
 * LVP（轻量虚拟机）后端预留同一接口，按 {@code app.range.backend} 选择实现。
 */
public interface RangeOrchestrator {

    /** 拉起一台靶机；同步返回运行时元数据（容器 ID、宿主映射端口等）。 */
    OrchestratorInstance start(InstanceSpec spec);

    /** 销毁靶机；幂等（实例不存在时静默返回）。 */
    void stop(String containerId);

    /** 销毁后重新拉起（同 spec）；默认实现 = stop + start。 */
    default OrchestratorInstance reset(InstanceSpec spec, String existingContainerId) {
        if (existingContainerId != null) {
            try { stop(existingContainerId); } catch (RuntimeException ignored) { /* 可能已被 orchestrator 自身清理 */ }
        }
        return start(spec);
    }

    /** 查询容器是否仍在运行；返回 true 视为实例活跃。 */
    boolean isRunning(String containerId);

    /** 后端名称，用于日志。 */
    String backendName();
}
