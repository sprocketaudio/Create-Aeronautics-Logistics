package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public record RouteStop(
        UUID id,
        String name,
        int pointIndex,
        WaitCondition waitCondition,
        Optional<BlockPos> dockPos
) {
    public RouteStop {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(waitCondition, "waitCondition");
        dockPos = Objects.requireNonNull(dockPos, "dockPos");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (pointIndex < 0) {
            throw new IllegalArgumentException("pointIndex must not be negative");
        }
    }

    public static RouteStop create(String name, int pointIndex, WaitCondition waitCondition) {
        return new RouteStop(UUID.randomUUID(), name, pointIndex, waitCondition, Optional.empty());
    }

    public RouteStop withWaitCondition(WaitCondition waitCondition) {
        return new RouteStop(id, name, pointIndex, waitCondition, dockPos);
    }
}
