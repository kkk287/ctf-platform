package com.example.ctf.service;

import com.example.ctf.common.BusinessException;
import com.example.ctf.common.ErrorCode;
import com.example.ctf.domain.Team;
import com.example.ctf.domain.User;
import com.example.ctf.dto.TeamDtos;
import com.example.ctf.repository.TeamRepository;
import com.example.ctf.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
public class TeamService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    public TeamService(TeamRepository teamRepository, UserRepository userRepository, AuthService authService) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Transactional
    public TeamDtos.TeamInfoDto create(String userId, TeamDtos.CreateTeamRequest request) {
        User user = authService.requireUser(userId);
        if (user.getTeam() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "你已加入战队，请先退出当前战队");
        }
        String name = request.name().trim();
        if (teamRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "战队名已存在");
        }
        Team team = new Team();
        team.setName(name);
        team.setInviteCode(generateCode());
        team.setCaptain(user);
        teamRepository.save(team);
        user.setTeam(team);
        userRepository.save(user);
        return toInfo(team);
    }

    @Transactional
    public TeamDtos.TeamInfoDto join(String userId, TeamDtos.JoinTeamRequest request) {
        User user = authService.requireUser(userId);
        if (user.getTeam() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "你已加入战队，请先退出当前战队");
        }
        Team team = teamRepository.findByInviteCode(request.inviteCode().trim().toUpperCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "邀请码无效"));
        user.setTeam(team);
        userRepository.save(user);
        return toInfo(team);
    }

    @Transactional
    public void leave(String userId) {
        User user = authService.requireUser(userId);
        Team team = user.getTeam();
        if (team == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "你当前没有战队");
        }
        List<User> members = userRepository.findByTeamIdOrderByCreatedAtAsc(team.getId());
        user.setTeam(null);
        userRepository.save(user);
        if (team.getCaptain().getId().equals(user.getId())) {
            List<User> others = members.stream().filter(m -> !m.getId().equals(user.getId())).toList();
            if (others.isEmpty()) {
                teamRepository.delete(team);
            } else {
                team.setCaptain(others.get(0));
                teamRepository.save(team);
            }
        }
    }

    @Transactional
    public void removeMember(String operatorId, String memberId) {
        User operator = authService.requireUser(operatorId);
        User member = authService.requireUser(memberId);
        Team team = operator.getTeam();
        if (team == null || !team.getId().equals(member.getTeam() == null ? null : member.getTeam().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能管理自己战队的成员");
        }
        if (!team.getCaptain().getId().equals(operator.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有队长可以移除成员");
        }
        if (member.getId().equals(operator.getId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "队长请使用退出战队功能");
        }
        member.setTeam(null);
        userRepository.save(member);
    }

    @Transactional(readOnly = true)
    public TeamDtos.TeamInfoDto myTeam(String userId) {
        User user = authService.requireUser(userId);
        return user.getTeam() == null ? null : toInfo(user.getTeam());
    }

    @Transactional(readOnly = true)
    public TeamDtos.TeamInfoDto teamInfo(String teamId) {
        return teamRepository.findById(teamId).map(this::toInfo).orElse(null);
    }

    public TeamDtos.TeamInfoDto toInfo(Team team) {
        List<TeamDtos.TeamMemberDto> members = userRepository.findByTeamIdOrderByCreatedAtAsc(team.getId()).stream()
                .map(u -> new TeamDtos.TeamMemberDto(u.getId(), u.getUsername(), u.getDisplayName(),
                        team.getCaptain().getId().equals(u.getId()), u.getCreatedAt()))
                .toList();
        return new TeamDtos.TeamInfoDto(team.getId(), team.getName(), team.getInviteCode(),
                team.getCaptain().getId(), team.getCaptain().getDisplayName(), members.size(), members,
                team.getCreatedAt());
    }

    private String generateCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                code.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            if (!teamRepository.existsByInviteCode(code.toString())) {
                return code.toString();
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "邀请码生成失败，请重试");
    }
}
