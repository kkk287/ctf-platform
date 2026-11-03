package com.example.ctf.controller;

import com.example.ctf.common.ApiResponse;
import com.example.ctf.dto.ScoreboardDtos;
import com.example.ctf.security.UserPrincipal;
import com.example.ctf.service.ScoreboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ScoreboardController {

    private final ScoreboardService scoreboardService;

    public ScoreboardController(ScoreboardService scoreboardService) {
        this.scoreboardService = scoreboardService;
    }

    @GetMapping("/scoreboard")
    public ApiResponse<ScoreboardDtos.ScoreboardResponse> scoreboard(
            @RequestParam(required = false) String eventId,
            @RequestParam(defaultValue = "USER") String type,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(scoreboardService.scoreboard(eventId, type, limit, principal));
    }

    @GetMapping("/scoreboard/recent")
    public ApiResponse<List<ScoreboardDtos.RecentSolveDto>> recent(
            @RequestParam(required = false) String eventId,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(scoreboardService.recent(eventId, limit, principal));
    }

    @GetMapping("/me/profile")
    public ApiResponse<ScoreboardDtos.ProfileResponse> profile(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(scoreboardService.profile(principal));
    }
}
