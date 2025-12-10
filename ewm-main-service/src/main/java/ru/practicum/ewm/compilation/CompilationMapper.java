package ru.practicum.ewm.compilation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.model.Compilation;
import ru.practicum.ewm.event.EventMapper;
import ru.practicum.ewm.event.dto.EventShortDto;

import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CompilationMapper {

    public static CompilationDto toDto(Compilation compilation,
                                       Map<Long, Long> confirmed,
                                       Map<Long, Long> views) {

        List<EventShortDto> events = compilation.getEvents().stream()
                .map(event -> EventMapper.toShortDto(
                        event,
                        confirmed.getOrDefault(event.getId(), 0L),
                        views.getOrDefault(event.getId(), 0L)))
                .toList();

        return CompilationDto.builder()
                .id(compilation.getId())
                .events(events)
                .pinned(compilation.getPinned())
                .title(compilation.getTitle())
                .build();
    }
}
