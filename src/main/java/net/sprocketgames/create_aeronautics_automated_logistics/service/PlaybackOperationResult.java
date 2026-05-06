package net.sprocketgames.create_aeronautics_automated_logistics.service;

import java.util.Objects;
import java.util.Optional;

public record PlaybackOperationResult<T>(Optional<T> value, Optional<PlaybackFailure> failure) {
    public PlaybackOperationResult {
        value = Objects.requireNonNull(value, "value");
        failure = Objects.requireNonNull(failure, "failure");

        if (value.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("result must contain exactly one value or failure");
        }
    }

    public static <T> PlaybackOperationResult<T> success(T value) {
        return new PlaybackOperationResult<>(Optional.of(Objects.requireNonNull(value, "value")), Optional.empty());
    }

    public static <T> PlaybackOperationResult<T> failure(PlaybackFailure failure) {
        return new PlaybackOperationResult<>(Optional.empty(), Optional.of(Objects.requireNonNull(failure, "failure")));
    }
}
