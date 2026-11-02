package com.example.ctf.controller;

import com.example.ctf.common.ApiResponse;
import com.example.ctf.dto.ChallengeDtos;
import com.example.ctf.domain.Category;
import com.example.ctf.security.UserPrincipal;
import com.example.ctf.service.ChallengeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    private final ChallengeService challengeService;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @GetMapping
    public ApiResponse<ChallengeDtos.ChallengeListResponse> list(
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Boolean solved,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(challengeService.list(eventId, category, solved, keyword, principal));
    }

    @GetMapping("/{id}")
    public ApiResponse<ChallengeDtos.ChallengeDetailDto> detail(
            @PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(challengeService.detail(id, principal));
    }

    @GetMapping("/{id}/attachment")
    public ResponseEntity<Resource> attachment(@PathVariable String id,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        ChallengeService.AttachmentDownload download = challengeService.attachment(id, principal);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(download.contentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new FileSystemResource(download.path()));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<ChallengeDtos.SubmitResponse> submit(
            @PathVariable String id, @Valid @RequestBody ChallengeDtos.SubmitRequest request,
            HttpServletRequest httpRequest, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(challengeService.submit(id, request.flag(), clientIp(httpRequest),
                userAgent(httpRequest), principal));
    }

    @PostMapping("/{id}/hints/{hintId}/unlock")
    public ApiResponse<ChallengeDtos.UnlockHintResponse> unlockHint(
            @PathVariable String id, @PathVariable String hintId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(challengeService.unlockHint(id, hintId, principal));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return ip.length() > 64 ? ip.substring(0, 64) : ip;
    }

    private String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) {
            return null;
        }
        return ua.length() > 300 ? ua.substring(0, 300) : ua;
    }
}
