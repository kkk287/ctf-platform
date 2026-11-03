package com.example.ctf.service;

import com.example.ctf.domain.AuditLog;
import com.example.ctf.repository.AuditLogRepository;
import com.example.ctf.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计服务：从安全上下文与请求上下文中提取操作者与来源 IP。
 * 使用 REQUIRES_NOT_SUPPORTED 挂起当前事务独立提交？——不，与业务同事务：
 * 管理操作失败时审计一并回滚，避免"操作未生效但留有日志"的歧义。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AuditService(AuditLogRepository auditLogRepository, ApplicationEventPublisher eventPublisher) {
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String target, String detail) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setTarget(truncate(target, 200));
        entry.setDetail(truncate(detail, 1000));
        entry.setIp(currentIp());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            entry.setActorId(principal.getId());
            entry.setActorName(principal.getUsername());
            entry.setActorRole(principal.getRole().name());
        } else {
            entry.setActorName("system");
            entry.setActorRole("SYSTEM");
        }
        try {
            auditLogRepository.save(entry);
            // 发布审计事件：Mongo 镜像等扩展订阅；落库失败则不发布，保持两侧一致
            eventPublisher.publishEvent(new AuditRecordedEvent(
                    entry.getAction(), entry.getActorId(), entry.getActorName(), entry.getActorRole(),
                    entry.getTarget(), entry.getDetail(), entry.getIp(), entry.getCreatedAt()));
        } catch (Exception ex) {
            // 审计写入失败不阻断主流程
            log.warn("审计日志写入失败 action={} target={}: {}", action, target, ex.getMessage());
        }
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
