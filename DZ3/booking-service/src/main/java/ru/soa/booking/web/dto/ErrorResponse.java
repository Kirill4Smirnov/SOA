package ru.soa.booking.web.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record ErrorResponse(String code, String message, Map<String, String> details, OffsetDateTime timestamp) {
    public ErrorResponse(String code, String message) {
        this(code, message, null, OffsetDateTime.now());
    }
}

