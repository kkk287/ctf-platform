package com.example.ctf.controller;

import com.example.ctf.common.ApiResponse;
import com.example.ctf.dto.TeamDtos;
import com.example.ctf.security.UserPrincipal;
import com.example.ctf.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<TeamDtos.TeamInfoDto> create(@AuthenticationPrincipal UserPrincipal principal,
                                                   @Valid @RequestBody TeamDtos.CreateTeamRequest request) {
        return ApiResponse.ok(teamService.create(principal.getId(), request));
    }

    @PostMapping("/join")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<TeamDtos.TeamInfoDto> join(@AuthenticationPrincipal UserPrincipal principal,
                                                  @Valid @RequestBody TeamDtos.JoinTeamRequest request) {
        return ApiResponse.ok(teamService.join(principal.getId(), request));
    }

    @PostMapping("/leave")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> leave(@AuthenticationPrincipal UserPrincipal principal) {
        teamService.leave(principal.getId());
        return ApiResponse.ok();
    }

    @GetMapping("/my")
    public ApiResponse<TeamDtos.TeamInfoDto> my(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(teamService.myTeam(principal.getId()));
    }

    @DeleteMapping("/my/members/{userId}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> removeMember(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable String userId) {
        teamService.removeMember(principal.getId(), userId);
        return ApiResponse.ok();
    }
}
