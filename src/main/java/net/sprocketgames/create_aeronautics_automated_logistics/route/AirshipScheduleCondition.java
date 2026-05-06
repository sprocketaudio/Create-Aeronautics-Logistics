package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Objects;

public record AirshipScheduleCondition(
        AirshipScheduleConditionType type,
        WaitCondition waitCondition
) {
    public AirshipScheduleCondition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(waitCondition, "waitCondition");
    }

    public static AirshipScheduleCondition scheduledDelay(WaitCondition waitCondition) {
        return new AirshipScheduleCondition(AirshipScheduleConditionType.SCHEDULED_DELAY, waitCondition);
    }
}
