package com.example.ctf.controller;

import com.example.ctf.common.ApiResponse;
import com.example.ctf.dto.AwdDtos;
import com.example.ctf.security.UserPrincipal;
import com.example.ctf.service.AwdService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 选手端 AWD：轮次列表、实时比分与剩余时间、提交 flag。 */
@RestController
@RequestMapping("/api/awd")
public class AwdController {

    private final AwdService awdService;

    public AwdController(AwdService awdService) {
        this.awdService = awdService;
    }

    @GetMapping("/rounds")
    public ApiResponse<List<AwdDtos.RoundDto>> rounds(@RequestParam(required = false) String eventId) {
        return ApiResponse.ok(awdService.listRounds(eventId));
    }

    @GetMapping("/rounds/{id}/scoreboard")
    public ApiResponse<AwdDtos.ScoreboardDto> scoreboard(@PathVariable String id,
                                                         @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(awdService.scoreboard(id, principal.getUsername()));
    }

    @PostMapping("/rounds/{id}/attack")
    @PreAuthorize("isAuthenticated() and authentication.principal.role != T(com.example.ctf.domain.Role).OBSERVER")
    public ApiResponse<AwdDtos.AttackResultDto> attack(@PathVariable String id,
                                                       @RequestBody AwdDtos.AttackRequest request,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(awdService.attack(id, principal.getUsername(), request.flag()));
    }
}
