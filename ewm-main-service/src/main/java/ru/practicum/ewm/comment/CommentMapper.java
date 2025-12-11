package ru.practicum.ewm.comment;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.util.DateTimeUtils;
import ru.practicum.ewm.user.UserMapper;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CommentMapper {

    public static CommentDto toDto(Comment comment) {
        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .author(UserMapper.toShortDto(comment.getAuthor()))
                .eventId(comment.getEvent().getId())
                .createdOn(DateTimeUtils.FORMATTER.format(comment.getCreatedOn()))
                .updatedOn(comment.getUpdatedOn() != null ? DateTimeUtils.FORMATTER.format(comment.getUpdatedOn()) : null)
                .status(comment.getStatus().name())
                .build();
    }
}
