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

    public static AirshipScheduleCondition untilDocked(WaitCondition waitCondition) {
        return new AirshipScheduleCondition(AirshipScheduleConditionType.SCHEDULED_DELAY, waitCondition);
    }

    public static AirshipScheduleCondition fromWaitCondition(WaitCondition waitCondition) {
        return switch (waitCondition.type()) {
            case UNTIL_ITEM_THRESHOLD -> new AirshipScheduleCondition(AirshipScheduleConditionType.ITEM_CARGO_CONDITION, waitCondition);
            case UNTIL_FLUID_THRESHOLD -> new AirshipScheduleCondition(AirshipScheduleConditionType.FLUID_CARGO_CONDITION, waitCondition);
            default -> scheduledDelay(waitCondition);
        };
    }
}
