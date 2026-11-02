package com.example.ctf.controller;

import com.example.ctf.common.ApiResponse;
import com.example.ctf.dto.EventDtos;
import com.example.ctf.dto.ScoreboardDtos;
import com.example.ctf.security.UserPrincipal;
import com.example.ctf.service.AdminService;
import com.example.ctf.service.EventService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class EventController {

    private final EventService eventService;
    private final AdminService adminService;

    public EventController(EventService eventService, AdminService adminService) {
        this.eventService = eventService;
        this.adminService = adminService;
    }

    @GetMapping("/events")
    public ApiResponse<List<EventDtos.EventDto>> events() {
        return ApiResponse.ok(eventService.listEvents());
    }

    @GetMapping("/events/{id}")
    public ApiResponse<EventDtos.EventDto> event(@PathVariable String id) {
        return ApiResponse.ok(eventService.getEventDto(id));
    }

    @GetMapping("/announcements")
    public ApiResponse<List<ScoreboardDtos.AnnouncementDto>> announcements(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(adminService.announcements());
    }
}
