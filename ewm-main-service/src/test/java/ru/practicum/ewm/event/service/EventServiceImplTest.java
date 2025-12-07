package ru.practicum.ewm.event.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.event.dto.NewEventDto;
import ru.practicum.ewm.event.dto.UpdateEventAdminRequest;
import ru.practicum.ewm.event.dto.LocationDto;
import ru.practicum.ewm.event.model.AdminStateAction;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.BadRequestException;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.request.repository.ParticipationRequestRepository;
import ru.practicum.ewm.stats.StatsService;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;
import ru.practicum.ewm.util.DateTimeUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ParticipationRequestRepository requestRepository;

    @Mock
    private StatsService statsService;

    @InjectMocks
    private EventServiceImpl eventService;

    @Test
    void addEventShouldFailWhenDateIsTooSoon() {
        long userId = 1L;
        long categoryId = 2L;
        LocalDateTime now = LocalDateTime.now();

        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId)));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(buildCategory(categoryId)));

        NewEventDto dto = NewEventDto.builder()
                .annotation("A very long description for annotation")
                .category(categoryId)
                .description("A very long description for the event details")
                .eventDate(DateTimeUtils.FORMATTER.format(now.plusMinutes(30)))
                .location(new LocationDto(55.0f, 37.0f))
                .title("Title for event")
                .build();

        assertThrows(ConflictException.class, () -> eventService.addEvent(userId, dto));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void adminPublishShouldFailWhenNotPending() {
        long eventId = 10L;
        Event event = Event.builder()
                .id(eventId)
                .state(EventState.PUBLISHED)
                .build();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        UpdateEventAdminRequest request = UpdateEventAdminRequest.builder()
                .stateAction(AdminStateAction.PUBLISH_EVENT)
                .build();

        assertThrows(ConflictException.class, () -> eventService.updateEventByAdmin(eventId, request));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void searchAdminEventsRejectsInvalidState() {
        assertThrows(BadRequestException.class,
                () -> eventService.searchAdminEvents(null, java.util.List.of("WRONG_STATE"), null, null, null, 0, 10));
    }

    private User buildUser(long id) {
        return User.builder()
                .id(id)
                .name("User " + id)
                .email("user" + id + "@mail.ru")
                .build();
    }

    private Category buildCategory(long id) {
        return Category.builder()
                .id(id)
                .name("Category " + id)
                .build();
    }
}
