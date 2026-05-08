package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Objects;
import net.minecraft.world.item.ItemStack;

public record WaitCondition(
        WaitConditionType type,
        int durationTicks,
        int idleTicks,
        int maxTicks,
        boolean failOnTimeout,
        int cargoOperator,
        int cargoMeasure,
        ItemStack cargoFilter
) {
    public static final int DEFAULT_TIMED_WAIT_TICKS = 20 * 5;

    public WaitCondition {
        Objects.requireNonNull(type, "type");
        durationTicks = Math.max(0, durationTicks);
        idleTicks = Math.max(0, idleTicks);
        maxTicks = Math.max(0, maxTicks);
        cargoOperator = Math.max(0, cargoOperator);
        cargoMeasure = Math.max(0, cargoMeasure);
        cargoFilter = cargoFilter == null ? ItemStack.EMPTY : cargoFilter.copy();
    }

    public static WaitCondition none() {
        return new WaitCondition(WaitConditionType.NONE, 0, 0, 0, false, 0, 0, ItemStack.EMPTY);
    }

    public static WaitCondition timed(int durationTicks) {
        return new WaitCondition(WaitConditionType.TIMED, durationTicks, 0, durationTicks, false, 0, 0, ItemStack.EMPTY);
    }

    public static WaitCondition untilDocked(int maxTicks) {
        return new WaitCondition(WaitConditionType.UNTIL_DOCKED, 0, 0, maxTicks, true, 0, 0, ItemStack.EMPTY);
    }

    public static WaitCondition untilIdle(int idleTicks, int maxTicks) {
        return new WaitCondition(WaitConditionType.UNTIL_IDLE, 0, idleTicks, maxTicks, true, 0, 0, ItemStack.EMPTY);
    }

    public static WaitCondition itemThreshold(int itemCount, int maxTicks) {
        return itemThreshold(itemCount, maxTicks, 0, 0, ItemStack.EMPTY);
    }

    public static WaitCondition itemThreshold(int itemCount, int maxTicks, int operator, int measure, ItemStack filter) {
        return new WaitCondition(WaitConditionType.UNTIL_ITEM_THRESHOLD, itemCount, 0, maxTicks, true, operator, measure, filter);
    }

    public static WaitCondition fluidThreshold(int fluidAmount, int maxTicks) {
        return fluidThreshold(fluidAmount, maxTicks, 0, 0, ItemStack.EMPTY);
    }

    public static WaitCondition fluidThreshold(int fluidAmount, int maxTicks, int operator, int measure, ItemStack filter) {
        return new WaitCondition(WaitConditionType.UNTIL_FLUID_THRESHOLD, fluidAmount, 0, maxTicks, true, operator, measure, filter);
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
            case UNTIL_IDLE -> idleTicks;
            default -> maxTicks > 0 ? maxTicks : durationTicks;
        };
    }
}
