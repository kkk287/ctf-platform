package com.example.ctf.controller;

import com.example.ctf.common.ApiResponse;
import com.example.ctf.dto.AwdDtos;
import com.example.ctf.service.AwdService;
import org.springframework.web.bind.annotation.*;

/**
 * 大屏匿名只读 AWD 接口：无需 JWT，仅返回队名/得分/排名/轮次/状态。
 * 不返回 flag、攻击记录明细、靶机 host/port/containerId。
 */
@RestController
@RequestMapping("/api/public/awd")
public class PublicAwdController {

    private final AwdService awdService;

    public PublicAwdController(AwdService awdService) {
        this.awdService = awdService;
    }

    @GetMapping("/rounds/current")
    public ApiResponse<AwdDtos.PublicScoreboardDto> current(@RequestParam(required = false) String eventId) {
        return ApiResponse.ok(awdService.currentPublicScoreboard(eventId));
    }

    @GetMapping("/rounds/{id}/scoreboard")
    public ApiResponse<AwdDtos.PublicScoreboardDto> scoreboard(@PathVariable String id) {
        return ApiResponse.ok(awdService.publicScoreboard(id));
    }
}
