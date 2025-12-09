package ru.practicum.ewm.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.practicum.ewm.event.model.Event;

import java.util.Set;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    boolean existsByCategoryId(Long categoryId);

    Set<Event> findAllByIdIn(Set<Long> ids);

    Set<Event> findAllByInitiatorId(Long initiatorId, org.springframework.data.domain.Pageable pageable);
}
