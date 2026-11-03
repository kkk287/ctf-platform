package com.example.ctf.controller;

import com.example.ctf.common.ApiResponse;
import com.example.ctf.dto.TheoryDtos;
import com.example.ctf.security.UserPrincipal;
import com.example.ctf.service.TheoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 理论赛：管理端题库/配置/总览，选手端取卷/提交/个人报告。 */
@RestController
@RequestMapping
public class TheoryController {

    private final TheoryService theoryService;

    public TheoryController(TheoryService theoryService) {
        this.theoryService = theoryService;
    }

    // ---------- 管理端 ----------

    @GetMapping("/api/admin/theory/questions")
    @PreAuthorize("hasAnyRole('ADMIN','JUDGE','TEACHER')")
    public ApiResponse<List<TheoryDtos.AdminQuestionDto>> listQuestions(
            @RequestParam(required = false) String eventId) {
        return ApiResponse.ok(theoryService.listQuestions(eventId));
    }

    @PostMapping("/api/admin/theory/questions")
    @PreAuthorize("hasAnyRole('ADMIN','JUDGE','TEACHER')")
    public ApiResponse<TheoryDtos.AdminQuestionDto> createQuestion(
            @RequestBody TheoryDtos.QuestionRequest request) {
        return ApiResponse.ok(theoryService.createQuestion(request));
    }

    @PutMapping("/api/admin/theory/questions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','JUDGE','TEACHER')")
    public ApiResponse<TheoryDtos.AdminQuestionDto> updateQuestion(
            @PathVariable String id, @RequestBody TheoryDtos.QuestionRequest request) {
        return ApiResponse.ok(theoryService.updateQuestion(id, request));
    }

    @DeleteMapping("/api/admin/theory/questions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','JUDGE','TEACHER')")
    public ApiResponse<Void> deleteQuestion(@PathVariable String id) {
        theoryService.deleteQuestion(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/theory/config")
    @PreAuthorize("hasAnyRole('ADMIN','JUDGE','TEACHER')")
    public ApiResponse<TheoryDtos.ConfigDto> getConfig(@RequestParam(required = false) String eventId) {
        return ApiResponse.ok(theoryService.getConfig(eventId));
    }

    @PutMapping("/api/admin/theory/config")
    @PreAuthorize("hasAnyRole('ADMIN','JUDGE','TEACHER')")
    public ApiResponse<TheoryDtos.ConfigDto> saveConfig(@RequestBody TheoryDtos.ConfigRequest request) {
        return ApiResponse.ok(theoryService.saveConfig(request));
    }

    @GetMapping("/api/admin/theory/report")
    @PreAuthorize("hasAnyRole('ADMIN','JUDGE','TEACHER')")
    public ApiResponse<TheoryDtos.OverviewDto> overview(@RequestParam(required = false) String eventId) {
        return ApiResponse.ok(theoryService.overview(eventId));
    }

    // ---------- 选手端 ----------

    /** 取卷：首次访问按配置抽题并物化空卷，之后返回同一份试卷。 */
    @GetMapping("/api/events/{eventId}/theory/paper")
    @PreAuthorize("isAuthenticated() and authentication.principal.role != T(com.example.ctf.domain.Role).OBSERVER")
    public ApiResponse<TheoryDtos.PaperResponse> paper(
            @PathVariable String eventId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(theoryService.paper(eventId, principal));
    }

    /** 提交判分：比赛进行期间允许重复提交覆盖；不返回正确答案。 */
    @PostMapping("/api/events/{eventId}/theory/submit")
    @PreAuthorize("isAuthenticated() and authentication.principal.role != T(com.example.ctf.domain.Role).OBSERVER")
    public ApiResponse<TheoryDtos.SubmitResultDto> submit(
            @PathVariable String eventId, @RequestBody TheoryDtos.SubmitRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(theoryService.submit(eventId, request, principal));
    }

    /** 个人能力报告：按知识点聚合得分率。 */
    @GetMapping("/api/events/{eventId}/theory/report")
    public ApiResponse<TheoryDtos.ReportDto> myReport(
            @PathVariable String eventId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(theoryService.myReport(eventId, principal));
    }
}
