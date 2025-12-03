package ru.practicum.statsclient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.practicum.statsdto.EndpointHit;
import ru.practicum.statsdto.ViewStats;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatsClient {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestClient restClient;

    public void hit(EndpointHit hit) {
        log.debug("Sending hit to stats service: {}", hit);

        restClient.post()
                .uri("/hit")
                .body(hit)
                .retrieve()
                .toBodilessEntity();
    }

    public List<ViewStats> getStats(LocalDateTime start,
                                    LocalDateTime end,
                                    List<String> uris,
                                    boolean unique) {

        String startStr = FORMATTER.format(start);
        String endStr = FORMATTER.format(end);

        var spec = restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/stats")
                            .queryParam("start", startStr)
                            .queryParam("end", endStr)
                            .queryParam("unique", unique);

                    if (uris != null && !uris.isEmpty()) {
                        builder.queryParam("uris", uris.toArray());
                    }
                    return builder.build();
                });

        return spec.retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
