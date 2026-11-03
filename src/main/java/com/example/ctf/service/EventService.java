package com.example.ctf.service;

import com.example.ctf.common.BusinessException;
import com.example.ctf.common.ErrorCode;
import com.example.ctf.domain.Event;
import com.example.ctf.domain.EventStatus;
import com.example.ctf.domain.EventType;
import com.example.ctf.dto.EventDtos;
import com.example.ctf.repository.ChallengeRepository;
import com.example.ctf.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final ChallengeRepository challengeRepository;
    private final DtoMapper mapper;
    private final AuditService auditService;

    public EventService(EventRepository eventRepository, ChallengeRepository challengeRepository,
                        DtoMapper mapper, AuditService auditService) {
        this.eventRepository = eventRepository;
        this.challengeRepository = challengeRepository;
        this.mapper = mapper;
        this.auditService = auditService;
    }

    public static EventStatus statusOf(Event event) {
        return statusOf(event, LocalDateTime.now());
    }

    public static EventStatus statusOf(Event event, LocalDateTime now) {
        if (event.getType() == EventType.PRACTICE) {
            return EventStatus.ONGOING;
        }
        if (event.getStartAt() == null || now.isBefore(event.getStartAt())) {
            return EventStatus.NOT_STARTED;
        }
        if (event.getEndAt() != null && now.isAfter(event.getEndAt())) {
            return EventStatus.ENDED;
        }
        return EventStatus.ONGOING;
    }

    @Transactional(readOnly = true)
    public List<EventDtos.EventDto> listEvents() {
        return eventRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventDtos.EventDto getEventDto(String eventId) {
        return toDto(requireEvent(eventId));
    }

    @Transactional(readOnly = true)
    public Event requireEvent(String eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "赛事不存在"));
    }

    /**
     * 默认赛事：优先进行中的比赛，其次练习区，最后最近结束的比赛。
     */
    @Transactional(readOnly = true)
    public Event defaultEvent() {
        List<Event> all = eventRepository.findAllByOrderByCreatedAtDesc();
        if (all.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "暂无赛事或练习区");
        }
        LocalDateTime now = LocalDateTime.now();
        return all.stream()
                .filter(e -> e.getType() == EventType.COMPETITION && statusOf(e, now) == EventStatus.ONGOING)
                .max(Comparator.comparing(Event::getStartAt))
                .or(() -> all.stream().filter(e -> e.getType() == EventType.PRACTICE).findFirst())
                .or(() -> all.stream()
                        .filter(e -> e.getType() == EventType.COMPETITION && statusOf(e, now) == EventStatus.ENDED)
                        .max(Comparator.comparing(Event::getEndAt)))
                .orElse(all.get(0));
    }

    @Transactional(readOnly = true)
    public Event resolveEvent(String eventId) {
        return eventId == null || eventId.isBlank() ? defaultEvent() : requireEvent(eventId);
    }

    /** 未开始的比赛对普通用户不可见题目；练习区始终可见 */
    public void assertViewable(Event event, boolean admin) {
        if (!admin && event.getType() == EventType.COMPETITION
                && statusOf(event) == EventStatus.NOT_STARTED) {
            throw new BusinessException(ErrorCode.EVENT_NOT_STARTED);
        }
    }

    /** 只有比赛进行中或练习区可以提交 */
    public void assertSubmittable(Event event) {
        if (event.getType() == EventType.PRACTICE) {
            return;
        }
        EventStatus status = statusOf(event);
        if (status == EventStatus.NOT_STARTED) {
            throw new BusinessException(ErrorCode.EVENT_NOT_STARTED);
        }
        if (status == EventStatus.ENDED) {
            throw new BusinessException(ErrorCode.EVENT_ENDED);
        }
    }

    @Transactional
    public EventDtos.EventDto create(EventDtos.EventRequest request) {
        Event event = new Event();
        apply(event, request);
        eventRepository.save(event);
        auditService.record("EVENT_CREATE", event.getName(), "命名空间 " + event.getNamespace());
        return toDto(event);
    }

    @Transactional
    public EventDtos.EventDto update(String id, EventDtos.EventRequest request) {
        Event event = requireEvent(id);
        apply(event, request);
        eventRepository.save(event);
        auditService.record("EVENT_UPDATE", event.getName(), "命名空间 " + event.getNamespace());
        return toDto(event);
    }

    @Transactional
    public void delete(String id) {
        Event event = requireEvent(id);
        if (challengeRepository.existsByEventId(id)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "请先删除该赛事下的所有题目");
        }
        eventRepository.delete(event);
        auditService.record("EVENT_DELETE", event.getName(), "命名空间 " + event.getNamespace());
    }

    private void apply(Event event, EventDtos.EventRequest request) {
        if (request.namespace() != null && !request.namespace().isBlank()) {
            String namespace = request.namespace().trim().toLowerCase();
            boolean conflict = event.getId() == null
                    ? eventRepository.existsByNamespace(namespace)
                    : eventRepository.existsByNamespaceAndIdNot(namespace, event.getId());
            if (conflict) {
                throw new BusinessException(ErrorCode.DUPLICATE, "命名空间已被占用");
            }
            event.setNamespace(namespace);
        }
        event.setName(request.name().trim());
        event.setDescription(request.description());
        event.setType(request.type());
        if (request.type() == EventType.PRACTICE) {
            event.setStartAt(null);
            event.setEndAt(null);
        } else {
            if (request.startAt() == null || request.endAt() == null) {
                throw new BusinessException(ErrorCode.INVALID_PARAM, "比赛必须设置开始与结束时间");
            }
            if (!request.startAt().isBefore(request.endAt())) {
                throw new BusinessException(ErrorCode.INVALID_PARAM, "开始时间必须早于结束时间");
            }
            event.setStartAt(request.startAt());
            event.setEndAt(request.endAt());
        }
    }

    private EventDtos.EventDto toDto(Event event) {
        long total = challengeRepository.countByEventId(event.getId());
        long visible = challengeRepository.countByEventIdAndVisibleTrue(event.getId());
        return mapper.eventDto(event, total, visible);
    }
}
