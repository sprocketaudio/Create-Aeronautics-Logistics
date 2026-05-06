package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Objects;
import java.util.Optional;

public record RouteRuntimeState(RouteStatus status, Optional<FailureReason> failureReason) {
    public RouteRuntimeState {
        Objects.requireNonNull(status, "status");
        failureReason = Objects.requireNonNull(failureReason, "failureReason");
    }

    public static RouteRuntimeState idle() {
        return new RouteRuntimeState(RouteStatus.IDLE, Optional.empty());
    }

    public static RouteRuntimeState failed(FailureReason reason) {
        return new RouteRuntimeState(RouteStatus.FAILED, Optional.of(Objects.requireNonNull(reason, "reason")));
    }
}
