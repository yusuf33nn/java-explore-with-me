package ru.practicum.ewm.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {

    private List<String> errors;
    private String message;
    private String reason;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    public static ApiError of(HttpStatus httpStatus, String reason, String message, List<String> errors) {
        List<String> errorDetails = errors != null ? errors : new ArrayList<>();
        return ApiError.builder()
                .errors(errorDetails)
                .message(message)
                .reason(reason)
                .status(httpStatus.name())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
