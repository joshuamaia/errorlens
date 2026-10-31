package dev.errorlens.infrastructure.observability;

import java.time.Instant;

public record LogRecord(int seq, Instant timestamp, String level, String logger, String thread, String message,
                        String exceptionClass) {
}
