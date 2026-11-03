package com.example.ctf.controller;

import com.example.ctf.common.ApiResponse;
import com.example.ctf.dto.AwdDtos;
import com.example.ctf.service.AwdService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 管理端 AWD：建轮次、起停推进、队伍分配、check 与实时比分。 */
@RestController
@RequestMapping("/api/admin/awd")
public class AdminAwdController {

    private final AwdService awdService;

    public AdminAwdController(AwdService awdService) {
        this.awdService = awdService;
    }

    @GetMapping("/rounds")
    public ApiResponse<List<AwdDtos.RoundDto>> rounds(@RequestParam(required = false) String eventId) {
        return ApiResponse.ok(awdService.listRounds(eventId));
    }

    @PostMapping("/rounds")
    public ApiResponse<AwdDtos.RoundDto> create(@RequestBody AwdDtos.CreateRoundRequest request) {
        return ApiResponse.ok(awdService.create(request));
    }

    @GetMapping("/rounds/{id}")
    public ApiResponse<AwdDtos.AdminDetailDto> detail(@PathVariable String id) {
        return ApiResponse.ok(awdService.adminDetail(id));
    }

    @PostMapping("/rounds/{id}/transition")
    public ApiResponse<AwdDtos.RoundDto> transition(@PathVariable String id,
                                                    @RequestBody AwdDtos.TransitionRequest request) {
        return ApiResponse.ok(awdService.transition(id, request.action()));
    }

    @PostMapping("/rounds/{id}/teams")
    public ApiResponse<AwdDtos.TeamDto> addTeam(@PathVariable String id,
                                                @RequestBody AwdDtos.TeamRequest request) {
        return ApiResponse.ok(awdService.addTeam(id, request));
    }

    @DeleteMapping("/rounds/{id}/teams/{teamId}")
    public ApiResponse<Void> removeTeam(@PathVariable String id, @PathVariable String teamId) {
        awdService.removeTeam(id, teamId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/rounds/{id}/checks")
    public ApiResponse<List<AwdDtos.CheckDto>> runChecks(@PathVariable String id) {
        return ApiResponse.ok(awdService.runChecks(id));
    }

    @GetMapping("/rounds/{id}/checks")
    public ApiResponse<List<AwdDtos.CheckDto>> checks(@PathVariable String id,
                                                      @RequestParam(defaultValue = "0") int roundNo) {
        return ApiResponse.ok(awdService.checkDtos(id, roundNo));
    }
}
