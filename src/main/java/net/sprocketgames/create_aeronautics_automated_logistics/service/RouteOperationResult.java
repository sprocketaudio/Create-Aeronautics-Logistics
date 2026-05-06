package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Objects;
import java.util.Optional;

public record RouteOperationResult<T>(Optional<T> value, Optional<RecordingFailure> failure) {
    public RouteOperationResult {
        value = Objects.requireNonNull(value, "value");
        failure = Objects.requireNonNull(failure, "failure");

        if (value.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("result must contain exactly one value or failure");
        }
    }

    public static <T> RouteOperationResult<T> success(T value) {
        return new RouteOperationResult<>(Optional.of(Objects.requireNonNull(value, "value")), Optional.empty());
    }

    public static <T> RouteOperationResult<T> failure(RecordingFailure failure) {
        return new RouteOperationResult<>(Optional.empty(), Optional.of(Objects.requireNonNull(failure, "failure")));
    }

    public boolean succeeded() {
        return value.isPresent();
    }
}
