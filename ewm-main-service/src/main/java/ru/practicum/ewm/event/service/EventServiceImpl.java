package ru.practicum.ewm.event.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.event.EventMapper;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventRequestStatusUpdateRequest;
import ru.practicum.ewm.event.dto.EventRequestStatusUpdateResult;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.dto.NewEventDto;
import ru.practicum.ewm.event.dto.UpdateEventAdminRequest;
import ru.practicum.ewm.event.dto.UpdateEventUserRequest;
import ru.practicum.ewm.event.model.AdminStateAction;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.model.Location;
import ru.practicum.ewm.event.model.UserStateAction;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.event.repository.EventSpecifications;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.request.RequestMapper;
import ru.practicum.ewm.request.dto.ParticipationRequestDto;
import ru.practicum.ewm.request.model.ParticipationRequest;
import ru.practicum.ewm.request.model.RequestStatus;
import ru.practicum.ewm.request.repository.ParticipationRequestRepository;
import ru.practicum.ewm.stats.StatsService;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;
import ru.practicum.ewm.util.DateTimeUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ParticipationRequestRepository requestRepository;
    private final StatsService statsService;

    private static final String REASON = "For the requested operation the conditions are not met.";

    @Override
    public EventFullDto addEvent(Long userId, NewEventDto dto) {
        User initiator = findUser(userId);
        Category category = findCategory(dto.getCategory());
        LocalDateTime eventDate = parseDate(dto.getEventDate());
        if (eventDate.isBefore(DateTimeUtils.now().plusHours(2))) {
            throw new ConflictException("Field: eventDate. Error: должно содержать дату, которая еще не наступила. Value: " + dto.getEventDate());
        }
        Event event = EventMapper.toEntity(dto, category, initiator);
        Event saved = eventRepository.save(event);
        return EventMapper.toFullDto(saved, 0L, 0L);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        findUser(userId);
        Pageable page = PageRequest.of(from / size, size, Sort.by("id"));
        List<Event> events = eventRepository.findAllByInitiatorId(userId, page);
        Map<Long, Long> confirmed = confirmedCounts(events);
        Map<Long, Long> views = views(events);
        return events.stream()
                .map(event -> EventMapper.toShortDto(event,
                        confirmed.getOrDefault(event.getId(), 0L),
                        views.getOrDefault(event.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getUserEvent(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        if (!Objects.equals(event.getInitiator().getId(), userId)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }
        return EventMapper.toFullDto(event,
                confirmedCount(event.getId()),
                views(Map.of(event.getId(), event)).getOrDefault(event.getId(), 0L));
    }

    @Override
    public EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        if (!Objects.equals(event.getInitiator().getId(), userId)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }
        if (request.getEventDate() != null) {
            LocalDateTime eventDate = parseDate(request.getEventDate());
            if (eventDate.isBefore(DateTimeUtils.now().plusHours(2))) {
                throw new ConflictException("Event date must be at least 2 hours after now");
            }
            event.setEventDate(eventDate);
        }
        applyCommonUpdates(request.getAnnotation(), request.getDescription(), request.getPaid(),
                request.getParticipantLimit(), request.getRequestModeration(), request.getTitle(),
                request.getLocation(), request.getCategory(), event);

        if (request.getStateAction() != null) {
            if (request.getStateAction() == UserStateAction.CANCEL_REVIEW) {
                event.setState(EventState.CANCELED);
            } else if (request.getStateAction() == UserStateAction.SEND_TO_REVIEW) {
                event.setState(EventState.PENDING);
            }
        }
        Event saved = eventRepository.save(event);
        return EventMapper.toFullDto(saved, confirmedCount(event.getId()), views(List.of(saved)).getOrDefault(event.getId(), 0L));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventFullDto> searchAdminEvents(List<Long> users, List<String> states, List<Long> categories,
                                                String rangeStart, String rangeEnd, int from, int size) {
        Pageable page = PageRequest.of(from / size, size);

        List<EventState> stateEnums = List.of();
        if (states != null) {
            try {
                stateEnums = states.stream()
                        .map(EventState::valueOf)
                        .toList();
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Incorrectly made request.");
            }
        }
        LocalDateTime start = parseDateOrNull(rangeStart);
        LocalDateTime end = parseDateOrNull(rangeEnd);

        List<Event> events = eventRepository.findAll(
                EventSpecifications.hasUsers(users)
                        .and(EventSpecifications.hasStates(stateEnums))
                        .and(EventSpecifications.hasCategories(categories))
                        .and(EventSpecifications.startAfter(start))
                        .and(EventSpecifications.endBefore(end)),
                page).getContent();

        Map<Long, Long> confirmed = confirmedCounts(events);
        Map<Long, Long> views = views(events);
        return events.stream()
                .map(event -> EventMapper.toFullDto(event,
                        confirmed.getOrDefault(event.getId(), 0L),
                        views.getOrDefault(event.getId(), 0L)))
                .toList();
    }

    @Override
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (request.getEventDate() != null) {
            LocalDateTime newDate = parseDate(request.getEventDate());
            if (newDate.isBefore(DateTimeUtils.now().plusHours(1))) {
                throw new ConflictException("Event date must be at least 1 hour after publication");
            }
            event.setEventDate(newDate);
        }

        applyCommonUpdates(request.getAnnotation(), request.getDescription(), request.getPaid(),
                request.getParticipantLimit(), request.getRequestModeration(), request.getTitle(),
                request.getLocation(), request.getCategory(), event);

        if (request.getStateAction() != null) {
            if (request.getStateAction() == AdminStateAction.PUBLISH_EVENT) {
                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException("Cannot publish the event because it's not in the right state: " + event.getState());
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(DateTimeUtils.now());
            } else if (request.getStateAction() == AdminStateAction.REJECT_EVENT) {
                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Cannot reject published event");
                }
                event.setState(EventState.CANCELED);
            }
        }

        Event saved = eventRepository.save(event);
        return EventMapper.toFullDto(saved, confirmedCount(saved.getId()), views(List.of(saved)).getOrDefault(saved.getId(), 0L));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventShortDto> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                               String rangeStart, String rangeEnd, Boolean onlyAvailable,
                                               String sort, int from, int size, HttpServletRequest request) {
        statsService.hit(request);
        LocalDateTime start = parseDateOrNull(rangeStart);
        LocalDateTime end = parseDateOrNull(rangeEnd);
        if (start == null && end == null) {
            start = DateTimeUtils.now();
        }

        Sort sorting = "EVENT_DATE".equals(sort) ? Sort.by("eventDate") : Sort.unsorted();
        Pageable page = PageRequest.of(from / size, size, sorting);

        List<Event> events = eventRepository.findAll(
                EventSpecifications.isPublished()
                        .and(EventSpecifications.textSearch(text))
                        .and(EventSpecifications.hasCategories(categories))
                        .and(EventSpecifications.paid(paid))
                        .and(EventSpecifications.startAfter(start))
                        .and(EventSpecifications.endBefore(end)),
                page).getContent();

        Map<Long, Long> confirmed = confirmedCounts(events);
        Map<Long, Long> views = views(events);

        List<EventShortDto> result = new ArrayList<>();
        for (Event event : events) {
            long confirmedCount = confirmed.getOrDefault(event.getId(), 0L);
            if (Boolean.TRUE.equals(onlyAvailable) && event.getParticipantLimit() != 0
                    && confirmedCount >= event.getParticipantLimit()) {
                continue;
            }
            result.add(EventMapper.toShortDto(event,
                    confirmedCount,
                    views.getOrDefault(event.getId(), 0L)));
        }

        if ("VIEWS".equals(sort)) {
            result = result.stream()
                    .sorted(Comparator.comparingLong(EventShortDto::getViews).reversed())
                    .toList();
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getPublicEvent(Long id, HttpServletRequest request) {
        statsService.hit(request);
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event with id=" + id + " was not found"));
        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + id + " was not found");
        }
        long confirmed = confirmedCount(id);
        long views = views(List.of(event)).getOrDefault(id, 0L);
        return EventMapper.toFullDto(event, confirmed, views);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        if (!Objects.equals(event.getInitiator().getId(), userId)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }
        return requestRepository.findAllByEventId(eventId).stream()
                .map(RequestMapper::toDto)
                .toList();
    }

    @Override
    public EventRequestStatusUpdateResult changeRequestStatus(Long userId, Long eventId,
                                                              EventRequestStatusUpdateRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        if (!Objects.equals(event.getInitiator().getId(), userId)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }
        if (event.getParticipantLimit() != null && event.getParticipantLimit() == 0) {
            return new EventRequestStatusUpdateResult();
        }

        List<ParticipationRequest> requests = requestRepository.findAllByIdIn(request.getRequestIds());
        EventRequestStatusUpdateResult result = new EventRequestStatusUpdateResult();
        long confirmedCount = confirmedCount(eventId);

        for (ParticipationRequest participationRequest : requests) {
            if (participationRequest.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Request must have status PENDING");
            }
            if (RequestStatus.CONFIRMED.equals(request.getStatus())) {
                if (event.getParticipantLimit() != 0 && confirmedCount >= event.getParticipantLimit()) {
                    throw new ConflictException("The participant limit has been reached");
                }
                participationRequest.setStatus(RequestStatus.CONFIRMED);
                confirmedCount++;
                result.getConfirmedRequests().add(RequestMapper.toDto(participationRequest));
            } else {
                participationRequest.setStatus(RequestStatus.REJECTED);
                result.getRejectedRequests().add(RequestMapper.toDto(participationRequest));
            }
        }

        requestRepository.saveAll(requests);

        if (event.getParticipantLimit() != 0 && confirmedCount >= event.getParticipantLimit()) {
            List<ParticipationRequest> pending = requestRepository.findAllByEventId(eventId).stream()
                    .filter(r -> r.getStatus() == RequestStatus.PENDING)
                    .toList();
            for (ParticipationRequest r : pending) {
                r.setStatus(RequestStatus.REJECTED);
                result.getRejectedRequests().add(RequestMapper.toDto(r));
            }
            requestRepository.saveAll(pending);
        }
        return result;
    }

    private void applyCommonUpdates(String annotation,
                                    String description,
                                    Boolean paid,
                                    Integer participantLimit,
                                    Boolean requestModeration,
                                    String title,
                                    ru.practicum.ewm.event.dto.LocationDto locationDto,
                                    Long categoryId,
                                    Event event) {
        if (annotation != null) {
            event.setAnnotation(annotation);
        }
        if (description != null) {
            event.setDescription(description);
        }
        if (paid != null) {
            event.setPaid(paid);
        }
        if (participantLimit != null) {
            event.setParticipantLimit(participantLimit);
        }
        if (requestModeration != null) {
            event.setRequestModeration(requestModeration);
        }
        if (title != null) {
            event.setTitle(title);
        }
        if (locationDto != null) {
            Location location = EventMapper.toLocation(locationDto);
            event.setLocation(location);
        }
        if (categoryId != null) {
            event.setCategory(findCategory(categoryId));
        }
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User with id=" + id + " was not found"));
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category with id=" + id + " was not found"));
    }

    private LocalDateTime parseDate(String date) {
        try {
            return DateTimeUtils.FORMATTER.parse(date, LocalDateTime::from);
        } catch (Exception ex) {
            throw new BadRequestException("Incorrectly made request.");
        }
    }

    private LocalDateTime parseDateOrNull(String date) {
        if (date == null) {
            return null;
        }
        return parseDate(date);
    }

    private Map<Long, Long> views(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }
        Map<Long, Event> map = events.stream().collect(Collectors.toMap(Event::getId, e -> e));
        return views(map);
    }

    private Map<Long, Long> views(Map<Long, Event> eventsById) {
        if (eventsById.isEmpty()) {
            return Map.of();
        }
        List<String> uris = eventsById.keySet().stream()
                .map(id -> "/events/" + id)
                .toList();
        LocalDateTime start = eventsById.values().stream()
                .map(Event::getCreatedOn)
                .min(LocalDateTime::compareTo)
                .orElse(DateTimeUtils.now().minusYears(1));

        Map<String, Long> stats = statsService.getViews(uris, start, DateTimeUtils.now());
        Map<Long, Long> result = new HashMap<>();
        for (Map.Entry<String, Long> entry : stats.entrySet()) {
            String uri = entry.getKey();
            Long id = Long.parseLong(uri.substring(uri.lastIndexOf("/") + 1));
            result.put(id, entry.getValue());
        }
        return result;
    }

    private Map<Long, Long> confirmedCounts(List<Event> events) {
        Map<Long, Long> result = new HashMap<>();
        for (Event event : events) {
            result.put(event.getId(), confirmedCount(event.getId()));
        }
        return result;
    }

    private long confirmedCount(Long eventId) {
        return requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
    }
}
