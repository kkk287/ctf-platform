package com.example.ctf.controller;

import com.example.ctf.common.ApiResponse;
import com.example.ctf.common.PageResult;
import com.example.ctf.dto.AdminDtos;
import com.example.ctf.dto.ChallengeDtos;
import com.example.ctf.dto.EventDtos;
import com.example.ctf.dto.ScoreboardDtos;
import com.example.ctf.domain.SubmissionResult;
import com.example.ctf.security.UserPrincipal;
import com.example.ctf.service.AdminService;
import com.example.ctf.service.ChallengeService;
import com.example.ctf.service.EventService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('ADMIN','JUDGE')")
public class AdminController {

    private final ChallengeService challengeService;
    private final EventService eventService;
    private final AdminService adminService;

    public AdminController(ChallengeService challengeService, EventService eventService,
                           AdminService adminService) {
        this.challengeService = challengeService;
        this.eventService = eventService;
        this.adminService = adminService;
    }

    @GetMapping("/challenges")
    public ApiResponse<List<ChallengeDtos.AdminChallengeDto>> challenges() {
        return ApiResponse.ok(challengeService.adminList());
    }

    @PostMapping("/challenges")
    public ApiResponse<ChallengeDtos.AdminChallengeDto> createChallenge(
            @Valid @RequestBody ChallengeDtos.AdminChallengeRequest request) {
        return ApiResponse.ok(challengeService.create(request));
    }

    @PutMapping("/challenges/{id}")
    public ApiResponse<ChallengeDtos.AdminChallengeDto> updateChallenge(
            @PathVariable String id, @Valid @RequestBody ChallengeDtos.AdminChallengeRequest request) {
        return ApiResponse.ok(challengeService.update(id, request));
    }

    @DeleteMapping("/challenges/{id}")
    public ApiResponse<Void> deleteChallenge(@PathVariable String id) {
        challengeService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/challenges/{id}/attachment")
    public ApiResponse<ChallengeDtos.AdminChallengeDto> uploadAttachment(
            @PathVariable String id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(challengeService.uploadAttachment(id, file));
    }

    @DeleteMapping("/challenges/{id}/attachment")
    public ApiResponse<ChallengeDtos.AdminChallengeDto> deleteAttachment(@PathVariable String id) {
        return ApiResponse.ok(challengeService.deleteAttachment(id));
    }

    @PostMapping("/challenges/{id}/hints")
    public ApiResponse<ChallengeDtos.HintDto> addHint(
            @PathVariable String id, @Valid @RequestBody ChallengeDtos.HintRequest request) {
        return ApiResponse.ok(challengeService.addHint(id, request));
    }

    @PutMapping("/challenges/{id}/hints/{hintId}")
    public ApiResponse<ChallengeDtos.HintDto> updateHint(
            @PathVariable String id, @PathVariable String hintId,
            @Valid @RequestBody ChallengeDtos.HintRequest request) {
        return ApiResponse.ok(challengeService.updateHint(id, hintId, request));
    }

    @DeleteMapping("/challenges/{id}/hints/{hintId}")
    public ApiResponse<Void> deleteHint(@PathVariable String id, @PathVariable String hintId) {
        challengeService.deleteHint(id, hintId);
        return ApiResponse.ok();
    }

    @GetMapping("/events")
    public ApiResponse<List<EventDtos.EventDto>> events() {
        return ApiResponse.ok(eventService.listEvents());
    }

    @PostMapping("/events")
    public ApiResponse<EventDtos.EventDto> createEvent(@Valid @RequestBody EventDtos.EventRequest request) {
        return ApiResponse.ok(eventService.create(request));
    }

    @PutMapping("/events/{id}")
    public ApiResponse<EventDtos.EventDto> updateEvent(@PathVariable String id,
                                                       @Valid @RequestBody EventDtos.EventRequest request) {
        return ApiResponse.ok(eventService.update(id, request));
    }

    @DeleteMapping("/events/{id}")
    public ApiResponse<Void> deleteEvent(@PathVariable String id) {
        eventService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<AdminDtos.AdminUserDto>> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(adminService.users(page, size, keyword));
    }

    @PatchMapping("/users/{id}/ban")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminDtos.AdminUserDto> ban(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable String id,
            @RequestBody AdminDtos.BanRequest request) {
        return ApiResponse.ok(adminService.ban(principal.getId(), id, request.banned()));
    }

    @PatchMapping("/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminDtos.AdminUserDto> updateRole(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable String id,
            @Valid @RequestBody AdminDtos.RoleUpdateRequest request) {
        return ApiResponse.ok(adminService.updateRole(principal.getId(), id, request.role()));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResult<AdminDtos.AuditLogDto>> auditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action) {
        return ApiResponse.ok(adminService.auditLogs(page, size, action));
    }

    @GetMapping("/submissions")
    public ApiResponse<PageResult<AdminDtos.SubmissionDto>> submissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String challengeId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) SubmissionResult result) {
        return ApiResponse.ok(adminService.submissions(page, size, challengeId, userId, result));
    }

    @GetMapping("/anomalies")
    public ApiResponse<List<AdminDtos.AnomalyDto>> anomalies() {
        return ApiResponse.ok(adminService.anomalies());
    }

    @GetMapping("/announcements")
    public ApiResponse<List<ScoreboardDtos.AnnouncementDto>> announcements() {
        return ApiResponse.ok(adminService.announcements());
    }

    @PostMapping("/announcements")
    public ApiResponse<ScoreboardDtos.AnnouncementDto> createAnnouncement(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminDtos.AnnouncementRequest request) {
        return ApiResponse.ok(adminService.createAnnouncement(principal.getId(), request));
    }

    @PutMapping("/announcements/{id}")
    public ApiResponse<ScoreboardDtos.AnnouncementDto> updateAnnouncement(
            @PathVariable String id, @Valid @RequestBody AdminDtos.AnnouncementRequest request) {
        return ApiResponse.ok(adminService.updateAnnouncement(id, request));
    }

    @DeleteMapping("/announcements/{id}")
    public ApiResponse<Void> deleteAnnouncement(@PathVariable String id) {
        adminService.deleteAnnouncement(id);
        return ApiResponse.ok();
    }
}
