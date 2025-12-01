package ru.practicum.statsserver.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.statsdto.EndpointHit;
import ru.practicum.statsdto.ViewStats;
import ru.practicum.statsserver.model.Hit;
import ru.practicum.statsserver.repository.HitRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsServiceImpl implements StatsService {

    public static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HitRepository repository;

    @Override
    @Transactional
    public EndpointHit saveHit(EndpointHit dto) {
        Hit entity = Hit.builder()
                .app(dto.getApp())
                .uri(dto.getUri())
                .ip(dto.getIp())
                .timestamp(LocalDateTime.parse(dto.getTimestamp(), FORMATTER))
                .build();

        entity = repository.save(entity);

        dto.setId(entity.getId());
        return dto;
    }

    @Override
    public List<ViewStats> getStats(LocalDateTime start,
                                    LocalDateTime end,
                                    List<String> uris,
                                    boolean unique) {

        boolean hasUris = uris != null && !uris.isEmpty();

        if (!hasUris && !unique) {
            return repository.getStats(start, end);
        } else if (!hasUris) {
            return repository.getStatsUnique(start, end);
        } else if (!unique) {
            return repository.getStatsByUris(start, end, uris);
        } else {
            return repository.getStatsUniqueByUris(start, end, uris);
        }
    }
}
