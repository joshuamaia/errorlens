package dev.errorlens.infrastructure.observability;

import java.time.Instant;

public record AttemptRecord(int number, String target, String operation, Instant startedAt, Long durationMs,
                            String outcome, Integer httpStatus, String errorClass, String circuitState,
                            String detail) {
}
