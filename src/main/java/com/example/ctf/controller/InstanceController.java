package com.example.ctf.controller;

import com.example.ctf.common.ApiResponse;
import com.example.ctf.dto.ChallengeDtos;
import com.example.ctf.security.UserPrincipal;
import com.example.ctf.service.InstanceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 动态靶机实例管理：选手端申请/重置/销毁/查询，管理端列表。 */
@RestController
@RequestMapping
public class InstanceController {

    private final InstanceService instanceService;

    public InstanceController(InstanceService instanceService) {
        this.instanceService = instanceService;
    }

    /** 申请靶机；已有活跃实例会延长 TTL 后直接返回。 */
    @PostMapping("/api/challenges/{id}/instance/start")
    @PreAuthorize("isAuthenticated() and authentication.principal.role != T(com.example.ctf.domain.Role).OBSERVER")
    public ApiResponse<ChallengeDtos.InstanceStatusDto> start(
            @PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(instanceService.start(id, principal.getId()));
    }

    /** 重置靶机（stop + start，新容器新端口）。 */
    @PostMapping("/api/challenges/{id}/instance/reset")
    @PreAuthorize("isAuthenticated() and authentication.principal.role != T(com.example.ctf.domain.Role).OBSERVER")
    public ApiResponse<ChallengeDtos.InstanceStatusDto> reset(
            @PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(instanceService.reset(id, principal.getId()));
    }

    /** 销毁当前活跃靶机（立即释放资源）。 */
    @DeleteMapping("/api/challenges/{id}/instance")
    @PreAuthorize("isAuthenticated() and authentication.principal.role != T(com.example.ctf.domain.Role).OBSERVER")
    public ApiResponse<Void> stop(
            @PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
        instanceService.stop(id, principal.getId());
        return ApiResponse.ok(null);
    }

    /** 查询当前题目下我的靶机状态（无则返回 null）。 */
    @GetMapping("/api/challenges/{id}/instance")
    public ApiResponse<ChallengeDtos.InstanceStatusDto> status(
            @PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(instanceService.status(id, principal.getId()));
    }

    /** 管理端：全量实例列表。 */
    @GetMapping("/api/admin/instances")
    @PreAuthorize("hasAnyRole('ADMIN','JUDGE')")
    public ApiResponse<java.util.List<ChallengeDtos.AdminInstanceDto>> adminList() {
        return ApiResponse.ok(instanceService.adminList());
    }
}
