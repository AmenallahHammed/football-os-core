package com.fos.sdk.core;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
    String code,
    String message,
    List<String> details,
    Instant timestamp,
    String correlationId
) {
    public static ErrorResponse of(String code, String message, String correlationId) {
        return new ErrorResponse(code, message, List.of(), Instant.now(), correlationId);
    }

    public static ErrorResponse of(String code, String message, List<String> details, String correlationId) {
        return new ErrorResponse(code, message, details, Instant.now(), correlationId);
    }
}
