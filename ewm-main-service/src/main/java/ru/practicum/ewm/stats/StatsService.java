package ru.practicum.ewm.stats;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.practicum.statsclient.StatsClient;
import ru.practicum.statsdto.EndpointHit;
import ru.practicum.statsdto.ViewStats;
import ru.practicum.ewm.util.DateTimeUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final StatsClient statsClient;

    @Value("${app.name:ewm-main-service}")
    private String appName;

    public void hit(HttpServletRequest request) {
        EndpointHit hit = EndpointHit.builder()
                .app(appName)
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .timestamp(DateTimeUtils.FORMATTER.format(LocalDateTime.now()))
                .build();
        statsClient.hit(hit);
    }

    public Map<String, Long> getViews(List<String> uris, LocalDateTime start, LocalDateTime end) {
        if (uris == null || uris.isEmpty()) {
            return Map.of();
        }
        List<ViewStats> stats = statsClient.getStats(start, end, uris, true);
        Map<String, Long> result = new HashMap<>();
        for (ViewStats stat : stats) {
            result.put(stat.getUri(), stat.getHits());
        }
        return result;
    }
}
