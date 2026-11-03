package com.example.ctf.range;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** 拉起靶机的输入规格（设计上覆盖 Docker 与 LVP 两种后端）。 */
public record InstanceSpec(
        String instanceId,                // 业务层实例 ID，用作 Docker 容器名
        String challengeId,
        String ownerId,
        String image,
        Integer containerPort,            // 容器内监听端口
        Integer hostPort,                 // 宿主对外端口（0 让 Docker 自动分配）
        String cpuLimit,                  // 可选，例如 "1.0"
        Integer memoryMb,                 // 可选
        Map<String, String> env,          // 注入容器的环境变量（flag 等）
        Map<String, String> labels) {     // 用于清理扫描

    public InstanceSpec(String instanceId, String challengeId, String ownerId,
                        String image, Integer containerPort) {
        this(instanceId, challengeId, ownerId, image, containerPort, 0, null, null, new HashMap<>(), new HashMap<>());
    }
}
