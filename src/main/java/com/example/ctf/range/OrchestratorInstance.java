package com.example.ctf.range;

import java.time.LocalDateTime;

/** Orchestrator 返回的实例视图（与 Domain.InstanceStatus 一一对应）。 */
public record OrchestratorInstance(
        String containerId,
        String containerName,
        String host,
        Integer port,                      // 宿主对外端口（Docker 分配时可能为 null，需再查一次）
        LocalDateTime startedAt) {
}
