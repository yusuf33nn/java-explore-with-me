package ru.practicum.ewm.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(NotFoundException ex) {
        log.warn("Not found: {}", ex.getMessage());
        return ApiError.of(HttpStatus.NOT_FOUND, "The required object was not found.", ex.getMessage(), null);
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleConflict(ConflictException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return ApiError.of(HttpStatus.CONFLICT, "For the requested operation the conditions are not met.", ex.getMessage(), null);
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ApiError.of(HttpStatus.BAD_REQUEST, "Incorrectly made request.", ex.getMessage(), null);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            NoHandlerFoundException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidation(Exception ex) {
        log.warn("Validation error: {}", ex.getMessage());
        return ApiError.of(HttpStatus.BAD_REQUEST, "Incorrectly made request.", ex.getMessage(), extractErrors(ex));
    }

    @ExceptionHandler(Throwable.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleOther(Throwable ex) {
        log.error("Unexpected error", ex);
        return ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error.", ex.getMessage(), extractErrors(ex));
    }

    private List<String> extractErrors(Throwable ex) {
        return List.of(ex.getMessage());
    }
}
