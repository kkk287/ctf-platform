package com.example.ctf.service;

import java.time.LocalDateTime;

/**
 * 审计事件：审计落库后发布，供 Mongo 镜像等扩展订阅。
 */
public record AuditRecordedEvent(String action, String actorId, String actorName, String actorRole,
                                 String target, String detail, String ip, LocalDateTime createdAt) {
}
