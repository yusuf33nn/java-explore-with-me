package ru.practicum.ewm.event;

import ru.practicum.ewm.category.CategoryMapper;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.event.dto.EventFullDto;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.dto.LocationDto;
import ru.practicum.ewm.event.dto.NewEventDto;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.Location;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.user.UserMapper;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.util.DateTimeUtils;

public final class EventMapper {

    private EventMapper() {
    }

    public static Event toEntity(NewEventDto dto, Category category, User initiator) {
        return Event.builder()
                .annotation(dto.getAnnotation())
                .description(dto.getDescription())
                .category(category)
                .eventDate(DateTimeUtils.FORMATTER.parse(dto.getEventDate(), java.time.LocalDateTime::from))
                .location(toLocation(dto.getLocation()))
                .paid(dto.getPaid() != null ? dto.getPaid() : Boolean.FALSE)
                .participantLimit(dto.getParticipantLimit() != null ? dto.getParticipantLimit() : 0)
                .requestModeration(dto.getRequestModeration() != null ? dto.getRequestModeration() : Boolean.TRUE)
                .title(dto.getTitle())
                .initiator(initiator)
                .state(EventState.PENDING)
                .createdOn(DateTimeUtils.now())
                .build();
    }

    public static EventFullDto toFullDto(Event event, long confirmed, long views) {
        return EventFullDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .category(CategoryMapper.toDto(event.getCategory()))
                .confirmedRequests(confirmed)
                .createdOn(format(event.getCreatedOn()))
                .description(event.getDescription())
                .eventDate(format(event.getEventDate()))
                .initiator(UserMapper.toShortDto(event.getInitiator()))
                .location(toDto(event.getLocation()))
                .paid(event.getPaid())
                .participantLimit(event.getParticipantLimit())
                .publishedOn(format(event.getPublishedOn()))
                .requestModeration(event.getRequestModeration())
                .state(event.getState().name())
                .title(event.getTitle())
                .views(views)
                .build();
    }

    public static EventShortDto toShortDto(Event event, long confirmed, long views) {
        return EventShortDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .category(CategoryMapper.toDto(event.getCategory()))
                .confirmedRequests(confirmed)
                .eventDate(format(event.getEventDate()))
                .initiator(UserMapper.toShortDto(event.getInitiator()))
                .paid(event.getPaid())
                .title(event.getTitle())
                .views(views)
                .build();
    }

    public static Location toLocation(LocationDto dto) {
        if (dto == null) {
            return null;
        }
        return Location.builder()
                .lat(dto.getLat())
                .lon(dto.getLon())
                .build();
    }

    public static LocationDto toDto(Location location) {
        if (location == null) {
            return null;
        }
        return LocationDto.builder()
                .lat(location.getLat())
                .lon(location.getLon())
                .build();
    }

    private static String format(java.time.LocalDateTime dateTime) {
        return dateTime == null ? null : DateTimeUtils.FORMATTER.format(dateTime);
    }
}
