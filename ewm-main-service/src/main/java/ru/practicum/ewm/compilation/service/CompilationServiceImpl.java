package ru.practicum.ewm.compilation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.compilation.CompilationMapper;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.dto.UpdateCompilationRequest;
import ru.practicum.ewm.compilation.model.Compilation;
import ru.practicum.ewm.compilation.repository.CompilationRepository;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.request.model.RequestStatus;
import ru.practicum.ewm.request.repository.ParticipationRequestRepository;
import ru.practicum.ewm.stats.StatsService;
import ru.practicum.ewm.util.DateTimeUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final ParticipationRequestRepository requestRepository;
    private final StatsService statsService;

    @Override
    public CompilationDto create(NewCompilationDto dto) {
        Compilation compilation = new Compilation();
        compilation.setTitle(dto.getTitle());
        compilation.setPinned(Boolean.TRUE.equals(dto.getPinned()));
        if (dto.getEvents() != null && !dto.getEvents().isEmpty()) {
            compilation.setEvents(fetchEvents(dto.getEvents()));
        }
        Compilation saved = compilationRepository.save(compilation);
        return toDto(saved);
    }

    @Override
    public void delete(Long compId) {
        if (!compilationRepository.existsById(compId)) {
            throw new NotFoundException("Compilation with id=" + compId + " was not found");
        }
        compilationRepository.deleteById(compId);
    }

    @Override
    public CompilationDto update(Long compId, UpdateCompilationRequest dto) {
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));
        if (dto.getTitle() != null) {
            compilation.setTitle(dto.getTitle());
        }
        if (dto.getPinned() != null) {
            compilation.setPinned(dto.getPinned());
        }
        if (dto.getEvents() != null) {
            compilation.setEvents(fetchEvents(dto.getEvents()));
        }
        Compilation saved = compilationRepository.save(compilation);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompilationDto> getCompilations(Boolean pinned, int from, int size) {
        Pageable page = PageRequest.of(from / size, size);
        List<Compilation> comps = compilationRepository.findAll(page).getContent();
        if (pinned != null) {
            comps = comps.stream()
                    .filter(c -> c.getPinned().equals(pinned))
                    .toList();
        }
        return comps.stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CompilationDto getCompilation(Long compId) {
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));
        return toDto(compilation);
    }

    private Set<Event> fetchEvents(Set<Long> ids) {
        return eventRepository.findAllById(ids).stream().collect(Collectors.toSet());
    }

    private CompilationDto toDto(Compilation compilation) {
        Map<Long, Long> confirmed = new HashMap<>();
        Map<Long, Event> eventsById = compilation.getEvents().stream()
                .collect(Collectors.toMap(Event::getId, e -> e));

        for (Event event : compilation.getEvents()) {
            confirmed.put(event.getId(),
                    requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED));
        }
        List<String> uris = eventsById.keySet().stream()
                .map(id -> "/events/" + id)
                .toList();
        Map<String, Long> stats = statsService.getViews(uris, DateTimeUtils.now().minusYears(1), DateTimeUtils.now());
        Map<Long, Long> views = new HashMap<>();
        for (Map.Entry<String, Long> entry : stats.entrySet()) {
            Long id = Long.parseLong(entry.getKey().substring(entry.getKey().lastIndexOf("/") + 1));
            views.put(id, entry.getValue());
        }
        return CompilationMapper.toDto(compilation, confirmed, views);
    }
}
