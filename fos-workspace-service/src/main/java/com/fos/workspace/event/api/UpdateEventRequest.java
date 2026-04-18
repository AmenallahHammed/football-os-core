package com.fos.workspace.event.api;

import java.time.Instant;

public record UpdateEventRequest(
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        String location
) {
}
