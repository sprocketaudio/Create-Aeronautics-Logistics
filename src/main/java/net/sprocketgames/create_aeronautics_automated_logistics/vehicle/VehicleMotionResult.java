package net.sprocketgames.create_aeronautics_automated_logistics.vehicle;

import java.util.Objects;
import java.util.Optional;
import net.sprocketgames.create_aeronautics_automated_logistics.route.FailureReason;

public record VehicleMotionResult(boolean successful, Optional<FailureReason> failureReason) {
    public VehicleMotionResult {
        failureReason = Objects.requireNonNull(failureReason, "failureReason");

        if (successful && failureReason.isPresent()) {
            throw new IllegalArgumentException("successful movement cannot include a failure reason");
        }
    }

    public static VehicleMotionResult moved() {
        return new VehicleMotionResult(true, Optional.empty());
    }

    public static VehicleMotionResult failed(FailureReason reason) {
        return new VehicleMotionResult(false, Optional.of(Objects.requireNonNull(reason, "reason")));
    }
}
