package net.sprocketgames.create_aeronautics_automated_logistics.identity;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record AirshipStationSnapshot(
        UUID stationId,
        String stationName,
        ResourceKey<Level> dimension,
        BlockPos stationPos
) {
    public AirshipStationSnapshot {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(stationName, "stationName");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(stationPos, "stationPos");
    }
}
