package com.example.ctf.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class TeamDtos {

    private TeamDtos() {
    }

    public record CreateTeamRequest(
            @NotBlank(message = "战队名不能为空")
            @Size(min = 2, max = 40, message = "战队名长度需为 2-40 个字符")
            String name) {
    }

    public record JoinTeamRequest(
            @NotBlank(message = "邀请码不能为空") String inviteCode) {
    }

    public record TeamMemberDto(String id, String username, String displayName, boolean captain,
                                LocalDateTime joinedAt) {
    }

    public record TeamInfoDto(String id, String name, String inviteCode, String captainId, String captainName,
                              long memberCount, List<TeamMemberDto> members, LocalDateTime createdAt) {
    }
}
