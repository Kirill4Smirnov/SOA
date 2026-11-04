package ru.soa.booking.web;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.soa.booking.web.dto.ErrorResponse;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(StatusRuntimeException.class)
    ResponseEntity<ErrorResponse> grpc(StatusRuntimeException exception) {
        Status.Code grpcCode = exception.getStatus().getCode();
        HttpStatus httpStatus = switch (grpcCode) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RESOURCE_EXHAUSTED, FAILED_PRECONDITION, ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case UNAVAILABLE, DEADLINE_EXCEEDED -> HttpStatus.SERVICE_UNAVAILABLE;
            case UNAUTHENTICATED, PERMISSION_DENIED -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.BAD_GATEWAY;
        };
        String code = grpcCode == Status.Code.UNAVAILABLE && isCircuitOpen(exception)
                ? "FLIGHT_SERVICE_CIRCUIT_OPEN" : "FLIGHT_SERVICE_" + grpcCode.name();
        String message = exception.getStatus().getDescription();
        if (message == null || message.isBlank()) message = "Ошибка Flight Service";
        return ResponseEntity.status(httpStatus).body(new ErrorResponse(code, message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> bodyValidation(MethodArgumentNotValidException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return validation(details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> parameterValidation(HandlerMethodValidationException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getAllValidationResults().forEach(result -> result.getResolvableErrors().forEach(error ->
                details.putIfAbsent(result.getMethodParameter().getParameterName(), error.getDefaultMessage())));
        return validation(details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return validation(Map.of(exception.getName(), "invalid value"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> missingParameter(MissingServletRequestParameterException exception) {
        return validation(Map.of(exception.getParameterName(), "required"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException exception) {
        return validation(Map.of("body", "invalid JSON or field value"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception exception) {
        log.error("Unhandled request error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "Внутренняя ошибка сервера"));
    }

    private ResponseEntity<ErrorResponse> validation(Map<String, String> details) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "VALIDATION_ERROR", "Некорректные входные данные", details, OffsetDateTime.now()));
    }

    private boolean isCircuitOpen(StatusRuntimeException exception) {
        String description = exception.getStatus().getDescription();
        return description != null && description.contains("circuit breaker is OPEN");
    }
}
