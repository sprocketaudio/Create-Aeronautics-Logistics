package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Objects;

public record WaitCondition(
        WaitConditionType type,
        int durationTicks,
        int idleTicks,
        int maxTicks,
        boolean failOnTimeout
) {
    public static final int DEFAULT_TIMED_WAIT_TICKS = 20 * 5;

    public WaitCondition {
        Objects.requireNonNull(type, "type");
        durationTicks = Math.max(0, durationTicks);
        idleTicks = Math.max(0, idleTicks);
        maxTicks = Math.max(0, maxTicks);
    }

    public static WaitCondition none() {
        return new WaitCondition(WaitConditionType.NONE, 0, 0, 0, false);
    }

    public static WaitCondition timed(int durationTicks) {
        return new WaitCondition(WaitConditionType.TIMED, durationTicks, 0, durationTicks, false);
    }

    public boolean waits() {
        return switch (type) {
            case NONE -> false;
            default -> true;
        };
    }

    public int runtimeTicks() {
        return switch (type) {
            case NONE -> 0;
            case TIMED -> durationTicks;
            default -> maxTicks > 0 ? maxTicks : durationTicks;
        };
    }
}
