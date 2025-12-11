package ru.practicum.ewm.comment.service;

import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.dto.NewCommentDto;

import java.util.List;

public interface CommentService {

    CommentDto addComment(Long userId, Long eventId, NewCommentDto dto);

    CommentDto updateOwnComment(Long userId, Long commentId, NewCommentDto dto);

    void deleteOwnComment(Long userId, Long commentId);

    List<CommentDto> getPublishedByEvent(Long eventId);

    CommentDto publish(Long commentId);

    CommentDto reject(Long commentId);

    void adminDelete(Long commentId);
}
