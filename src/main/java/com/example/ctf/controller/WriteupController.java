package com.example.ctf.controller;

import com.example.ctf.common.ApiResponse;
import com.example.ctf.dto.WriteupDtos;
import com.example.ctf.security.UserPrincipal;
import com.example.ctf.service.WriteupService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Writeup：参赛选手提交赛后解题报告，管理端审核。 */
@RestController
@RequestMapping("/api")
public class WriteupController {

    private final WriteupService writeupService;

    public WriteupController(WriteupService writeupService) {
        this.writeupService = writeupService;
    }

    // ---------- 选手端 ----------

    /** 提交 Writeup（已解出该题才能创建）。 */
    @PostMapping("/events/{eventId}/writeups")
    @PreAuthorize("isAuthenticated() and authentication.principal.role != T(com.example.ctf.domain.Role).OBSERVER")
    public ApiResponse<WriteupDtos.WriteupDto> create(
            @PathVariable String eventId,
            @RequestBody WriteupDtos.WriteupRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(writeupService.create(eventId, request, principal));
    }

    /** 编辑自己的 Writeup。 */
    @PutMapping("/writeups/{id}")
    @PreAuthorize("isAuthenticated() and authentication.principal.role != T(com.example.ctf.domain.Role).OBSERVER")
    public ApiResponse<WriteupDtos.WriteupDto> update(
            @PathVariable String id,
            @RequestBody WriteupDtos.WriteupRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(writeupService.update(id, request, principal));
    }

    /** 删除自己的 Writeup。 */
    @DeleteMapping("/writeups/{id}")
    @PreAuthorize("isAuthenticated() and authentication.principal.role != T(com.example.ctf.domain.Role).OBSERVER")
    public ApiResponse<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        writeupService.delete(id, principal);
        return ApiResponse.ok(null);
    }

    /** 查看 Writeup 详情：赛后公开可见，非赛后仅作者/管理员可看。 */
    @GetMapping("/writeups/{id}")
    public ApiResponse<WriteupDtos.WriteupDto> detail(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(writeupService.detail(id, principal));
    }

    /** 列表：赛事/题目维度，可见性受赛事状态与用户角色约束。 */
    @GetMapping("/events/{eventId}/writeups")
    public ApiResponse<List<WriteupDtos.SummaryDto>> list(
            @PathVariable String eventId,
            @RequestParam(required = false) String challengeId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(writeupService.list(eventId, challengeId, principal));
    }

    // ---------- 管理端 ----------

    @GetMapping("/admin/writeups")
    @PreAuthorize("hasAnyRole('ADMIN','JUDGE','TEACHER')")
    public ApiResponse<List<WriteupDtos.SummaryDto>> adminList(
            @RequestParam(required = false) String eventId) {
        return ApiResponse.ok(writeupService.adminList(eventId));
    }

    @PutMapping("/admin/writeups/{id}/moderate")
    @PreAuthorize("hasAnyRole('ADMIN','JUDGE','TEACHER')")
    public ApiResponse<WriteupDtos.WriteupDto> moderate(
            @PathVariable String id,
            @RequestBody WriteupDtos.ModerateRequest request) {
        return ApiResponse.ok(writeupService.moderate(id, request));
    }
}
