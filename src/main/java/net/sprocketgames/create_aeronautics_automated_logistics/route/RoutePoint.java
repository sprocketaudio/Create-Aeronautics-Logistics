package net.sprocketgames.create_aeronautics_automated_logistics.route;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record RoutePoint(
        Vec3 position,
        Optional<Float> yaw,
        Optional<RouteRotation> rotation,
        long tickOffset,
        ResourceKey<Level> dimension
) {
    public RoutePoint {
        Objects.requireNonNull(position, "position");
        yaw = Objects.requireNonNull(yaw, "yaw");
        rotation = Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(dimension, "dimension");

        if (tickOffset < 0L) {
            throw new IllegalArgumentException("tickOffset must be non-negative");
        }
    }

    public static RoutePoint withoutYaw(Vec3 position, long tickOffset, ResourceKey<Level> dimension) {
        return new RoutePoint(position, Optional.empty(), Optional.empty(), tickOffset, dimension);
    }

    public static RoutePoint withYaw(Vec3 position, float yaw, long tickOffset, ResourceKey<Level> dimension) {
        return new RoutePoint(position, Optional.of(yaw), Optional.empty(), tickOffset, dimension);
    }

    public static RoutePoint withRotation(
            Vec3 position,
            Optional<Float> yaw,
            RouteRotation rotation,
            long tickOffset,
            ResourceKey<Level> dimension
    ) {
        return new RoutePoint(position, yaw, Optional.of(rotation), tickOffset, dimension);
    }
}
