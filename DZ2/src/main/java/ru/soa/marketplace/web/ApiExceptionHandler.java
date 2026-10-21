package ru.soa.marketplace.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import ru.soa.marketplace.generated.model.ErrorResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(error(
                exception.getErrorCode(), exception.getMessage(), exception.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            details.compute(fieldError.getField(), (key, oldValue) -> append(oldValue, fieldError.getDefaultMessage()));
        }
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", "Некорректные входные данные", details));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException exception) {
        Map<String, Object> details = new LinkedHashMap<>();
        exception.getAllValidationResults().forEach(result -> result.getResolvableErrors().forEach(error ->
                details.compute(result.getMethodParameter().getParameterName(),
                        (key, oldValue) -> append(oldValue, error.getDefaultMessage()))));
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", "Некорректные параметры запроса", details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(error(
                "VALIDATION_ERROR", "Тело запроса имеет неверный формат", Map.of("body", "invalid JSON or enum value")));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(
                "INTERNAL_ERROR", "Внутренняя ошибка сервера", null));
    }

    public static ErrorResponse error(String code, String message, Map<String, Object> details) {
        ErrorResponse response = new ErrorResponse(ErrorResponse.ErrorCodeEnum.fromValue(code), message);
        response.setDetails(details);
        return response;
    }

    @SuppressWarnings("unchecked")
    private List<String> append(Object oldValue, String value) {
        if (oldValue == null) return new java.util.ArrayList<>(List.of(value));
        List<String> values = (List<String>) oldValue;
        values.add(value);
        return values;
    }
}
