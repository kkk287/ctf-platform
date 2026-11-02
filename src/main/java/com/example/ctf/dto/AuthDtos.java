package com.example.ctf.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空")
            @Pattern(regexp = "[a-zA-Z0-9_]{3,20}", message = "用户名需为 3-20 位字母、数字或下划线")
            String username,
            @NotBlank(message = "密码不能为空")
            @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
            String password,
            @Size(max = 60, message = "昵称最多 60 个字符") String displayName) {
    }

    public record UserDto(String id, String username, String displayName, String role, String roleLabel,
                          boolean banned, String teamId, String teamName) {
    }

    public record LoginResponse(String token, long expiresInSeconds, UserDto user) {
    }
}
