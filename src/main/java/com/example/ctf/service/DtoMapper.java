package com.example.ctf.service;

import com.example.ctf.domain.Announcement;
import com.example.ctf.domain.Event;
import com.example.ctf.domain.Role;
import com.example.ctf.domain.User;
import com.example.ctf.dto.AuthDtos;
import com.example.ctf.dto.EventDtos;
import com.example.ctf.dto.ScoreboardDtos;
import org.springframework.stereotype.Component;

@Component
public class DtoMapper {

    public AuthDtos.UserDto userDto(User user) {
        String teamId = user.getTeam() == null ? null : user.getTeam().getId();
        String teamName = user.getTeam() == null ? null : user.getTeam().getName();
        return new AuthDtos.UserDto(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole().name(),
                roleLabel(user.getRole()), user.isBanned(), teamId, teamName);
    }

    public EventDtos.EventDto eventDto(Event event, long challengeCount, long visibleChallengeCount) {
        var status = EventService.statusOf(event);
        return new EventDtos.EventDto(event.getId(), event.getNamespace(), event.getName(), event.getDescription(),
                event.getType(), event.getType().label(), status.name(), status.label(), event.getStartAt(),
                event.getEndAt(), event.isBlackoutMode(), challengeCount, visibleChallengeCount,
                event.getCreatedAt());
    }

    public ScoreboardDtos.AnnouncementDto announcementDto(Announcement announcement) {
        return new ScoreboardDtos.AnnouncementDto(announcement.getId(), announcement.getTitle(),
                announcement.getContent(), announcement.getCreatedAt());
    }

    public String roleLabel(Role role) {
        return switch (role) {
            case ADMIN -> "管理员";
            case JUDGE -> "裁判";
            case TEACHER -> "教师";
            case OBSERVER -> "观摩";
            case USER -> "选手";
        };
    }
}
