package ru.practicum.ewm.request.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.request.dto.ParticipationRequestDto;
import ru.practicum.ewm.request.model.ParticipationRequest;
import ru.practicum.ewm.request.model.RequestStatus;
import ru.practicum.ewm.request.repository.ParticipationRequestRepository;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestServiceImplTest {

    @Mock
    private ParticipationRequestRepository requestRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RequestServiceImpl requestService;

    @Test
    void addRequestShouldFailForInitiator() {
        long userId = 1L;
        long eventId = 5L;

        User initiator = buildUser(userId);
        Event event = Event.builder()
                .id(eventId)
                .initiator(initiator)
                .state(EventState.PUBLISHED)
                .participantLimit(0)
                .requestModeration(true)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(initiator));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(ConflictException.class, () -> requestService.addRequest(userId, eventId));
        verify(requestRepository, never()).save(any(ParticipationRequest.class));
    }

    @Test
    void cancelRequestShouldMarkCanceled() {
        long userId = 3L;
        long requestId = 7L;

        ParticipationRequest request = ParticipationRequest.builder()
                .id(requestId)
                .created(LocalDateTime.now())
                .requester(buildUser(userId))
                .event(Event.builder().id(11L).build())
                .status(RequestStatus.PENDING)
                .build();

        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(ParticipationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ParticipationRequestDto dto = requestService.cancelRequest(userId, requestId);

        assertEquals(RequestStatus.CANCELED.name(), dto.getStatus());
        verify(requestRepository).save(any(ParticipationRequest.class));
    }

    private User buildUser(long id) {
        return User.builder()
                .id(id)
                .name("User " + id)
                .email("user" + id + "@mail.ru")
                .build();
    }
}
